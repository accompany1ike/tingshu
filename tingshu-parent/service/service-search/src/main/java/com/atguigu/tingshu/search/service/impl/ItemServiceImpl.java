package com.atguigu.tingshu.search.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.search.service.ItemService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.search.AlbumDetailVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 专辑详情：详情数据在专辑库与用户库，接口层直接查库压力大，这里做三级防护
 * <p>
 * 1.布隆过滤器：专辑ID 一定不存在时直接返回，挡住「随便传个ID」的穿透请求；
 * 2.Redis 缓存：命中即返回，查不到的专辑也缓存一个空对象（短过期时间），防止同一个不存在的ID反复打库；
 * 3.分布式锁：缓存失效瞬间只放一个请求去查库，其余请求等锁后读缓存，避免缓存击穿
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class ItemServiceImpl implements ItemService {

	/** 布隆过滤器参数：预计存放的专辑数量与误判率 */
	private static final long BLOOM_FILTER_EXPECTED_INSERTIONS = 100000;
	private static final double BLOOM_FILTER_FALSE_PROBABILITY = 0.01;

	@Autowired
	private AlbumFeignClient albumFeignClient;

	@Autowired
	private UserFeignClient userFeignClient;

	@Autowired
	private RedisTemplate redisTemplate;

	@Autowired
	private RedissonClient redissonClient;


	/**
	 * 查询专辑详情
	 *
	 * @param albumId 专辑ID
	 * @return 专辑详情，专辑不存在时返回 null
	 */
	@Override
	public AlbumDetailVo getAlbumInfo(Long albumId) {
		if (albumId == null) {
			return null;
		}

		//1.布隆过滤器：判断专辑一定不存在，直接返回（误判只会多放行少量请求，不会漏掉真实存在的专辑）
		if (!this.getAlbumBloomFilter().contains(albumId)) {
			log.warn("专辑不存在，已被布隆过滤器拦截，专辑ID：{}", albumId);
			return null;
		}

		//2.查缓存
		String cacheKey = RedisConstant.ALBUM_INFO_PREFIX + albumId;
		AlbumDetailVo cacheValue = (AlbumDetailVo) redisTemplate.opsForValue().get(cacheKey);
		if (cacheValue != null) {
			//缓存里的空对象代表「专辑不存在」，短时间内的重复请求直接挡掉
			return cacheValue.getAlbumInfo() == null ? null : cacheValue;
		}

		//3.缓存没有：加分布式锁，只让一个线程去查库，其余线程拿不到锁就自己查（不阻塞用户）
		RLock lock = redissonClient.getLock(RedisConstant.ALBUM_LOCK_PREFIX + albumId);
		boolean locked = false;
		try {
			locked = lock.tryLock(RedisConstant.ALBUM_LOCK_WAIT_PX1, RedisConstant.ALBUM_LOCK_EXPIRE_PX2, TimeUnit.SECONDS);
			if (locked) {
				//3.1 抢到锁的线程再查一次缓存：等锁期间可能已经有别的线程把数据放进去了
				cacheValue = (AlbumDetailVo) redisTemplate.opsForValue().get(cacheKey);
				if (cacheValue != null) {
					return cacheValue.getAlbumInfo() == null ? null : cacheValue;
				}
			}

			//3.2 查库并组装详情
			AlbumDetailVo albumDetailVo = this.loadAlbumInfo(albumId);

			//3.3 只有抢到锁的线程负责回写缓存
			if (locked) {
				if (albumDetailVo == null) {
					redisTemplate.opsForValue().set(cacheKey, new AlbumDetailVo(),
							RedisConstant.ALBUM_TEMPORARY_TIMEOUT, TimeUnit.SECONDS);
				} else {
					redisTemplate.opsForValue().set(cacheKey, albumDetailVo,
							RedisConstant.ALBUM_TIMEOUT, TimeUnit.SECONDS);
				}
			}
			return albumDetailVo;
		} catch (InterruptedException e) {
			//等待锁被中断：恢复中断标记，降级为直接查库，不能因为锁的问题让用户打不开页面
			Thread.currentThread().interrupt();
			log.error("查询专辑详情等待锁被中断，专辑ID：{}", albumId, e);
			return this.loadAlbumInfo(albumId);
		} catch (Exception e) {
			log.error("查询专辑详情失败，专辑ID：{}", albumId, e);
			return null;
		} finally {
			if (locked && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}


	/**
	 * 查询数据库并组装专辑详情
	 * <p>
	 * 数据来自两个服务：专辑本体、分类、统计来自专辑模块，主播昵称与头像来自用户模块
	 *
	 * @param albumId 专辑ID
	 * @return 专辑详情，专辑不存在时返回 null
	 */
	private AlbumDetailVo loadAlbumInfo(Long albumId) {
		//1.专辑本体（含标签集合）
		AlbumInfo albumInfo = this.getData(albumFeignClient.getAlbumInfoById(albumId));
		if (albumInfo == null) {
			log.warn("专辑不存在，专辑ID：{}", albumId);
			return null;
		}

		AlbumDetailVo albumDetailVo = new AlbumDetailVo();
		albumDetailVo.setAlbumInfo(albumInfo);

		//2.一、二、三级分类：详情页要展示分类名称
		albumDetailVo.setBaseCategoryView(this.getData(albumFeignClient.getBaseCategoryView(albumInfo.getCategory3Id())));

		//3.统计信息：播放量、订阅量、购买量、评论数
		albumDetailVo.setAlbumStatVo(this.getData(albumFeignClient.getAlbumStatVo(albumId)));

		//4.主播信息：只回传昵称与头像，其它字段（尤其是微信openId）不能出服务
		UserInfoVo announcer = this.getData(userFeignClient.getUserInfo(albumInfo.getUserId()));
		if (announcer != null) {
			announcer.setWxOpenId(null);
			announcer.setVipExpireTime(null);
		}
		albumDetailVo.setAnnouncer(announcer);
		return albumDetailVo;
	}


	/**
	 * 远程调用结果统一取值：服务降级、业务异常时 data 都是 null
	 *
	 * @param result 远程调用结果
	 * @return 业务数据
	 */
	private <T> T getData(Result<T> result) {
		return result == null ? null : result.getData();
	}


	/**
	 * 获取专辑布隆过滤器（已经初始化过时 tryInit 直接返回 false，可以放心重复调用）
	 *
	 * @return 布隆过滤器
	 */
	private RBloomFilter<Long> getAlbumBloomFilter() {
		RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
		bloomFilter.tryInit(BLOOM_FILTER_EXPECTED_INSERTIONS, BLOOM_FILTER_FALSE_PROBABILITY);
		return bloomFilter;
	}

}
