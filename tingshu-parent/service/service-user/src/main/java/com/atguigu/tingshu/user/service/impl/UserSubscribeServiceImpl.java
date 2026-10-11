package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.user.UserSubscribe;
import com.atguigu.tingshu.user.mq.UserStatMqSender;
import com.atguigu.tingshu.user.service.UserSubscribeService;
import com.atguigu.tingshu.vo.user.UserSubscribeVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户订阅专辑
 * <p>
 * 订阅关系存在 MongoDB，按 userId 取模分表（userSubscribe_{userId%100}），
 * 集合名统一走 MongoUtil，不在业务里手写。
 * <p>
 * 专辑订阅量存在专辑库，订阅/取消订阅后发 MQ 让专辑模块累加，用户模块不直接写别人的库
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserSubscribeServiceImpl implements UserSubscribeService {

	/** 单页最大条数：防止前端传个超大分页把 Mongo 拖垮 */
	private static final int MAX_PAGE_SIZE = 100;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private AlbumFeignClient albumFeignClient;

	@Autowired
	private UserStatMqSender userStatMqSender;


	/**
	 * 订阅 / 取消订阅专辑
	 *
	 * @param userId  当前登录用户ID
	 * @param albumId 专辑ID
	 * @return true-订阅成功；false-取消订阅成功
	 */
	@Override
	public Boolean subscribeAlbum(Long userId, Long albumId) {
		//1.校验
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (albumId == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑ID不能为空");
		}
		AlbumInfo albumInfo = this.getData(albumFeignClient.getAlbumInfoById(albumId));
		if (albumInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "专辑不存在");
		}

		//2.已订阅就取消、没订阅就新增：一个按钮来回切，前端不区分两个接口
		String collectionName = this.collectionName(userId);
		Query query = new Query(Criteria.where("userId").is(userId).and("albumId").is(albumId));
		boolean subscribed = mongoTemplate.exists(query, UserSubscribe.class, collectionName);
		if (subscribed) {
			mongoTemplate.remove(query, UserSubscribe.class, collectionName);
			//3.订阅量 -1
			userStatMqSender.sendSubscribeStat(albumId, -1);
			log.info("取消订阅成功，用户ID：{}，专辑ID：{}", userId, albumId);
			return false;
		}

		UserSubscribe userSubscribe = new UserSubscribe();
		userSubscribe.setUserId(userId);
		userSubscribe.setAlbumId(albumId);
		userSubscribe.setCreateTime(new Date());
		mongoTemplate.insert(userSubscribe, collectionName);
		//3.订阅量 +1
		userStatMqSender.sendSubscribeStat(albumId, 1);
		log.info("订阅成功，用户ID：{}，专辑ID：{}", userId, albumId);
		return true;
	}


	/**
	 * 查询当前用户是否订阅了某张专辑
	 *
	 * @param userId  当前登录用户ID，未登录传 null
	 * @param albumId 专辑ID
	 * @return true-已订阅
	 */
	@Override
	public Boolean isSubscribeAlbum(Long userId, Long albumId) {
		//未登录直接返回 false：详情页拿它决定按钮文案，返回 null 前端会当异常处理
		if (userId == null || albumId == null) {
			return false;
		}
		Query query = new Query(Criteria.where("userId").is(userId).and("albumId").is(albumId));
		return mongoTemplate.exists(query, UserSubscribe.class, this.collectionName(userId));
	}


	/**
	 * 分页查询当前用户订阅的专辑
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码
	 * @param limit  每页条数
	 * @return 订阅专辑分页结果
	 */
	@Override
	public Page<UserSubscribeVo> findUserSubscribePage(Long userId, int page, int limit) {
		int pageNo = Math.max(page, 1);
		int pageSize = limit < 1 ? 10 : Math.min(limit, MAX_PAGE_SIZE);
		Page<UserSubscribeVo> pageResult = new Page<>(pageNo, pageSize);
		if (userId == null) {
			return pageResult;
		}

		//1.订阅关系分页：最新订阅的排前面
		String collectionName = this.collectionName(userId);
		Query query = new Query(Criteria.where("userId").is(userId));
		long total = mongoTemplate.count(query, UserSubscribe.class, collectionName);
		pageResult.setTotal(total);
		if (total == 0) {
			return pageResult;
		}
		query.with(Sort.by(Sort.Direction.DESC, "createTime"))
				.skip((long) (pageNo - 1) * pageSize)
				.limit(pageSize);
		List<UserSubscribe> subscribeList = mongoTemplate.find(query, UserSubscribe.class, collectionName);
		if (CollectionUtils.isEmpty(subscribeList)) {
			return pageResult;
		}

		//2.批量补专辑信息：逐条查会打出几十个请求
		List<Long> albumIdList = subscribeList.stream()
				.map(UserSubscribe::getAlbumId).filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<AlbumInfo> albumInfoList = this.getData(albumFeignClient.findAlbumInfoByIds(albumIdList));
		Map<Long, AlbumInfo> albumInfoMap = new HashMap<>();
		if (!CollectionUtils.isEmpty(albumInfoList)) {
			albumInfoList.forEach(albumInfo -> albumInfoMap.put(albumInfo.getId(), albumInfo));
		}

		//3.组装：专辑被删掉时只保留订阅时间，前端列表里那一条会显示成空白，不会报错
		List<UserSubscribeVo> records = new ArrayList<>();
		for (UserSubscribe userSubscribe : subscribeList) {
			UserSubscribeVo subscribeVo = new UserSubscribeVo();
			subscribeVo.setAlbumId(userSubscribe.getAlbumId());
			subscribeVo.setCreateTime(userSubscribe.getCreateTime());
			AlbumInfo albumInfo = albumInfoMap.get(userSubscribe.getAlbumId());
			if (albumInfo != null) {
				subscribeVo.setAlbumTitle(albumInfo.getAlbumTitle());
				subscribeVo.setCoverUrl(albumInfo.getCoverUrl());
				subscribeVo.setIncludeTrackCount(albumInfo.getIncludeTrackCount());
				subscribeVo.setIsFinished(albumInfo.getIsFinished());
			}
			records.add(subscribeVo);
		}
		pageResult.setRecords(records);
		return pageResult;
	}


	/**
	 * @param userId 用户ID
	 * @return 订阅集合名：userSubscribe_{userId%100}
	 */
	private String collectionName(Long userId) {
		return MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_SUBSCRIBE, userId);
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
