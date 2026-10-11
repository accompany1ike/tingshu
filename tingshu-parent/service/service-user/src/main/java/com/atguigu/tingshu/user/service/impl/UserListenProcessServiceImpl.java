package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.user.mq.ListenProcessMqSender;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.user.UserListenProcessListVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserListenProcessServiceImpl implements UserListenProcessService {

	/** 单页最大条数：防止前端传个超大分页把 Mongo 拖垮 */
	private static final int MAX_PAGE_SIZE = 100;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private ListenProcessMqSender listenProcessMqSender;

	@Autowired
	private AlbumFeignClient albumFeignClient;


	/**
	 * 更新播放进度
	 * <p>
	 * 前端播放页每 10 秒上报一次，所以这里必须是「覆盖更新」而不是「新增」，
	 * 否则一个用户听一集声音会在 Mongo 里堆出成百上千条记录
	 *
	 * @param userId              当前登录用户ID
	 * @param userListenProcessVo 播放进度（专辑ID、声音ID、跳出秒数）
	 */
	@Override
	public void updateListenProcess(Long userId, UserListenProcessVo userListenProcessVo) {
		//1.校验：专辑、声音、进度三个字段缺一不可，前端上报时理论上都会带上
		if (userListenProcessVo == null || userListenProcessVo.getAlbumId() == null
				|| userListenProcessVo.getTrackId() == null || userListenProcessVo.getBreakSecond() == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "播放进度参数不完整");
		}
		//进度不允许为负，个别机型拖动进度条时可能上报 -1
		BigDecimal breakSecond = userListenProcessVo.getBreakSecond().max(BigDecimal.ZERO);

		//2.写入 MongoDB：同一个「用户 + 专辑 + 声音」只有一条记录，反复上报只刷新跳出位置
		this.saveListenProcess(userId, userListenProcessVo.getAlbumId(),
				userListenProcessVo.getTrackId(), breakSecond);

		//3.发 MQ 通知专辑模块更新播放量统计（声音播放量 + 专辑播放量）
		listenProcessMqSender.sendPlayStat(userListenProcessVo.getAlbumId(), userListenProcessVo.getTrackId());
	}


	/**
	 * 查询某个声音上次播放的跳出秒数（播放页续播用）
	 *
	 * @param userId  当前登录用户ID
	 * @param trackId 声音ID
	 * @return 跳出秒数；没有播放记录时返回 0
	 */
	@Override
	public BigDecimal getTrackBreakSecond(Long userId, Long trackId) {
		if (userId == null || trackId == null) {
			return BigDecimal.ZERO;
		}
		String collectionName = this.collectionName(userId);
		Query query = new Query(Criteria.where("userId").is(userId).and("trackId").is(trackId))
				.with(Sort.by(Sort.Direction.DESC, "updateTime"))
				.limit(1);
		UserListenProcess listenProcess = mongoTemplate.findOne(query, UserListenProcess.class, collectionName);
		if (listenProcess == null || listenProcess.getBreakSecond() == null) {
			return BigDecimal.ZERO;
		}
		return listenProcess.getBreakSecond();
	}


	/**
	 * 查询用户最近一次播放的声音（"听专辑"入口续播用）
	 * <p>
	 * 只查 Mongo 不回源 MySQL：播放页拿到 trackId/albumId 后还会自己去查声音详情，
	 * 这里多查一次专辑库只会拖慢续播
	 *
	 * @param userId 当前登录用户ID
	 * @return 最近一次播放记录；没有历史时返回 albumId/trackId 为 0 的空对象
	 */
	@Override
	public UserListenProcessListVo getLatelyTrack(Long userId) {
		//1.先给出兜底结果：没有历史时前端会拿到 0，走"没有可续播内容"的分支
		UserListenProcessListVo latelyTrack = new UserListenProcessListVo();
		latelyTrack.setAlbumId(0L);
		latelyTrack.setTrackId(0L);
		latelyTrack.setBreakSecond(BigDecimal.ZERO);
		if (userId == null) {
			return latelyTrack;
		}

		//2.按更新时间倒序取最近一条
		String collectionName = this.collectionName(userId);
		Query query = new Query(Criteria.where("userId").is(userId)
				//trackId 为 0 表示这条记录是"浏览专辑"而不是"播放声音"，不能用来续播
				.and("trackId").gt(0)
				.and("isShow").is(1))
				.with(Sort.by(Sort.Direction.DESC, "updateTime"))
				.limit(1);
		UserListenProcess listenProcess = mongoTemplate.findOne(query, UserListenProcess.class, collectionName);
		if (listenProcess == null) {
			log.info("用户没有可续播的播放记录，用户ID：{}", userId);
			return latelyTrack;
		}

		//3.回填续播需要的最小字段（标题、封面等展示字段留给播放历史列表接口去补）
		latelyTrack.setId(listenProcess.getId());
		latelyTrack.setAlbumId(listenProcess.getAlbumId());
		latelyTrack.setTrackId(listenProcess.getTrackId());
		latelyTrack.setBreakSecond(listenProcess.getBreakSecond() == null
				? BigDecimal.ZERO : listenProcess.getBreakSecond());
		log.info("查询最近一次播放记录，用户ID：{}，专辑ID：{}，声音ID：{}，跳出秒数：{}",
				userId, latelyTrack.getAlbumId(), latelyTrack.getTrackId(), latelyTrack.getBreakSecond());
		return latelyTrack;
	}


	/**
	 * 分页查询当前用户的播放历史（我的-历史）
	 * <p>
	 * 只查 Mongo 拿不到标题封面，这里回源专辑模块补齐；
	 * 按声音ID批量查、再按专辑ID批量查，一页最多两次远程调用
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码
	 * @param limit  每页条数
	 * @return 播放历史分页结果
	 */
	@Override
	public Page<UserListenProcessListVo> findUserPage(Long userId, int page, int limit) {
		int pageNo = Math.max(page, 1);
		int pageSize = limit < 1 ? 10 : Math.min(limit, MAX_PAGE_SIZE);
		Page<UserListenProcessListVo> pageResult = new Page<>(pageNo, pageSize);
		if (userId == null) {
			return pageResult;
		}

		//1.播放记录分页：最近听的在最前面
		//  trackId 为 0 的是"浏览专辑"记录，已删除的记录 isShow=0，都不属于播放历史
		String collectionName = this.collectionName(userId);
		Query query = new Query(Criteria.where("userId").is(userId)
				.and("trackId").gt(0)
				.and("isShow").is(1));
		long total = mongoTemplate.count(query, UserListenProcess.class, collectionName);
		pageResult.setTotal(total);
		if (total == 0) {
			return pageResult;
		}
		query.with(Sort.by(Sort.Direction.DESC, "updateTime"))
				.skip((long) (pageNo - 1) * pageSize)
				.limit(pageSize);
		List<UserListenProcess> listenProcessList = mongoTemplate.find(query, UserListenProcess.class, collectionName);
		if (CollectionUtils.isEmpty(listenProcessList)) {
			return pageResult;
		}

		//2.批量补声音信息
		List<Long> trackIdList = listenProcessList.stream()
				.map(UserListenProcess::getTrackId).filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<TrackInfo> trackInfoList = this.getData(albumFeignClient.findTrackInfoByIds(trackIdList));
		Map<Long, TrackInfo> trackInfoMap = new HashMap<>();
		if (!CollectionUtils.isEmpty(trackInfoList)) {
			trackInfoList.forEach(trackInfo -> trackInfoMap.put(trackInfo.getId(), trackInfo));
		}

		//3.批量补专辑标题
		List<Long> albumIdList = trackInfoList == null ? Collections.emptyList()
				: trackInfoList.stream().map(TrackInfo::getAlbumId).filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<AlbumInfo> albumInfoList = this.getData(albumFeignClient.findAlbumInfoByIds(albumIdList));
		Map<Long, AlbumInfo> albumInfoMap = new HashMap<>();
		if (!CollectionUtils.isEmpty(albumInfoList)) {
			albumInfoList.forEach(albumInfo -> albumInfoMap.put(albumInfo.getId(), albumInfo));
		}

		//4.组装：声音被下架时只保留跳出位置，那一条会显示成空白，不会报错
		List<UserListenProcessListVo> records = new ArrayList<>();
		for (UserListenProcess listenProcess : listenProcessList) {
			UserListenProcessListVo listVo = new UserListenProcessListVo();
			listVo.setId(listenProcess.getId());
			listVo.setAlbumId(listenProcess.getAlbumId());
			listVo.setTrackId(listenProcess.getTrackId());
			listVo.setBreakSecond(listenProcess.getBreakSecond() == null
					? BigDecimal.ZERO : listenProcess.getBreakSecond());
			TrackInfo trackInfo = trackInfoMap.get(listenProcess.getTrackId());
			if (trackInfo != null) {
				listVo.setTrackTitle(trackInfo.getTrackTitle());
				listVo.setMediaDuration(trackInfo.getMediaDuration());
				listVo.setCoverUrl(trackInfo.getCoverUrl());
				AlbumInfo albumInfo = albumInfoMap.get(trackInfo.getAlbumId());
				if (albumInfo != null) {
					listVo.setAlbumTitle(albumInfo.getAlbumTitle());
					if (!StringUtils.hasText(listVo.getCoverUrl())) {
						listVo.setCoverUrl(albumInfo.getCoverUrl());
					}
				}
				listVo.setPlayRate(this.calculatePlayRate(listVo.getBreakSecond(), trackInfo.getMediaDuration()));
			}
			records.add(listVo);
		}
		pageResult.setRecords(records);
		return pageResult;
	}


	/**
	 * 删除一条播放历史（我的-历史-左滑删除）
	 * <p>
	 * 物理删除：播放历史是可以再生的数据（重听就会写回来），
	 * 保留一条 isShow=0 的软删除记录只会让集合越滚越大
	 *
	 * @param userId 当前登录用户ID
	 * @param id     播放记录ID（Mongo _id）
	 */
	@Override
	public void deleteListenProcess(Long userId, String id) {
		if (userId == null || !StringUtils.hasText(id)) {
			return;
		}
		//带上 userId 条件，避免拿到别人的 _id 就能删掉别人的历史
		Query query = new Query(Criteria.where("_id").is(id).and("userId").is(userId));
		mongoTemplate.remove(query, UserListenProcess.class, this.collectionName(userId));
		log.info("删除播放历史成功，用户ID：{}，记录ID：{}", userId, id);
	}


	/**
	 * 计算播放比例
	 * <p>
	 * 前端直接把 playRate 当文本渲染（例如「已听 40%」），所以返回带百分号的字符串；
	 * 时长缺失或为 0 时返回 0%，进度超过时长（换集时上报延迟）时按 100% 封顶
	 *
	 * @param breakSecond   跳出秒数
	 * @param mediaDuration 声音总时长
	 * @return 形如「40%」的播放比例
	 */
	private String calculatePlayRate(BigDecimal breakSecond, BigDecimal mediaDuration) {
		if (breakSecond == null || mediaDuration == null || mediaDuration.compareTo(BigDecimal.ZERO) <= 0) {
			return "0%";
		}
		BigDecimal rate = breakSecond.multiply(BigDecimal.valueOf(100))
				.divide(mediaDuration, 0, RoundingMode.HALF_UP);
		if (rate.compareTo(BigDecimal.valueOf(100)) > 0) {
			rate = BigDecimal.valueOf(100);
		}
		return rate + "%";
	}


	/**
	 * 覆盖写入一条播放进度
	 * <p>
	 * 用 upsert 而不是「先查再判断新增还是更新」：播放页上报是并发的，
	 * 先查再写会有两条记录同时插入的窗口
	 *
	 * @param userId      用户ID
	 * @param albumId     专辑ID
	 * @param trackId     声音ID
	 * @param breakSecond 跳出秒数
	 */
	private void saveListenProcess(Long userId, Long albumId, Long trackId, BigDecimal breakSecond) {
		String collectionName = this.collectionName(userId);
		Date now = new Date();

		Query query = new Query(Criteria.where("userId").is(userId)
				.and("albumId").is(albumId)
				.and("trackId").is(trackId));
		Update update = new Update()
				.set("userId", userId)
				.set("albumId", albumId)
				.set("trackId", trackId)
				.set("breakSecond", breakSecond)
				//播放历史列表只展示 isShow=1 的记录，重新播放时把它恢复回来
				.set("isShow", 1)
				.set("updateTime", now)
				//只有新增时才写创建时间，后续上报不覆盖
				.setOnInsert("createTime", now);

		mongoTemplate.upsert(query, update, collectionName);
		log.info("保存播放进度成功，用户ID：{}，专辑ID：{}，声音ID：{}，跳出秒数：{}",
				userId, albumId, trackId, breakSecond);
	}


	/**
	 * 取用户对应的 MongoDB 集合名
	 * <p>
	 * 播放进度按用户ID取模分表（userListenProcess_0 ~ userListenProcess_99），
	 * 单表数据量可控；集合名统一由 MongoUtil 生成，避免各处手写拼错
	 *
	 * @param userId 用户ID
	 * @return 集合名
	 */
	private String collectionName(Long userId) {
		return MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId);
	}


	/**
	 * 远程调用结果统一取值：服务降级、业务异常时 data 都是 null，调用方按「没查到」处理
	 *
	 * @param result 远程调用结果
	 * @return 业务数据
	 */
	private <T> T getData(Result<T> result) {
		return result == null ? null : result.getData();
	}

}
