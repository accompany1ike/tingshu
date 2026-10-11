package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserCollect;
import com.atguigu.tingshu.user.mq.UserStatMqSender;
import com.atguigu.tingshu.user.service.UserCollectService;
import com.atguigu.tingshu.vo.user.UserCollectVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户收藏声音
 * <p>
 * 收藏关系存在 MongoDB，按 userId 取模分表（userCollect_{userId%100}）；
 * 声音收藏量存在专辑库，收藏/取消收藏后发 MQ 让专辑模块累加
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserCollectServiceImpl implements UserCollectService {

	/** 单页最大条数：防止前端传个超大分页把 Mongo 拖垮 */
	private static final int MAX_PAGE_SIZE = 100;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private AlbumFeignClient albumFeignClient;

	@Autowired
	private UserStatMqSender userStatMqSender;


	/**
	 * 收藏 / 取消收藏声音
	 *
	 * @param userId  当前登录用户ID
	 * @param trackId 声音ID
	 * @return true-收藏成功；false-取消收藏成功
	 */
	@Override
	public Boolean collectTrack(Long userId, Long trackId) {
		//1.校验：声音必须存在，顺便拿到它属于哪张专辑（收藏量是按专辑分表统计的）
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (trackId == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "声音ID不能为空");
		}
		List<TrackInfo> trackInfoList = this.getData(albumFeignClient.findTrackInfoByIds(List.of(trackId)));
		if (CollectionUtils.isEmpty(trackInfoList)) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "声音不存在");
		}
		TrackInfo trackInfo = trackInfoList.get(0);

		//2.已收藏就取消、没收藏就新增：一个按钮来回切，前端不区分两个接口
		String collectionName = this.collectionName(userId);
		Query query = new Query(Criteria.where("userId").is(userId).and("trackId").is(trackId));
		boolean collected = mongoTemplate.exists(query, UserCollect.class, collectionName);
		if (collected) {
			mongoTemplate.remove(query, UserCollect.class, collectionName);
			userStatMqSender.sendCollectStat(trackInfo.getAlbumId(), trackId, -1);
			log.info("取消收藏成功，用户ID：{}，声音ID：{}", userId, trackId);
			return false;
		}

		UserCollect userCollect = new UserCollect();
		userCollect.setUserId(userId);
		userCollect.setTrackId(trackId);
		userCollect.setCreateTime(new Date());
		mongoTemplate.insert(userCollect, collectionName);
		userStatMqSender.sendCollectStat(trackInfo.getAlbumId(), trackId, 1);
		log.info("收藏成功，用户ID：{}，声音ID：{}", userId, trackId);
		return true;
	}


	/**
	 * 查询当前用户是否收藏了某个声音
	 *
	 * @param userId  当前登录用户ID，未登录传 null
	 * @param trackId 声音ID
	 * @return true-已收藏
	 */
	@Override
	public Boolean isCollectTrack(Long userId, Long trackId) {
		//未登录直接返回 false：播放页拿它决定图标高亮，返回 null 前端会当异常处理
		if (userId == null || trackId == null) {
			return false;
		}
		Query query = new Query(Criteria.where("userId").is(userId).and("trackId").is(trackId));
		return mongoTemplate.exists(query, UserCollect.class, this.collectionName(userId));
	}


	/**
	 * 分页查询当前用户收藏的声音
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码
	 * @param limit  每页条数
	 * @return 收藏声音分页结果
	 */
	@Override
	public Page<UserCollectVo> findUserCollectPage(Long userId, int page, int limit) {
		int pageNo = Math.max(page, 1);
		int pageSize = limit < 1 ? 10 : Math.min(limit, MAX_PAGE_SIZE);
		Page<UserCollectVo> pageResult = new Page<>(pageNo, pageSize);
		if (userId == null) {
			return pageResult;
		}

		//1.收藏关系分页：最新收藏的排前面
		String collectionName = this.collectionName(userId);
		Query query = new Query(Criteria.where("userId").is(userId));
		long total = mongoTemplate.count(query, UserCollect.class, collectionName);
		pageResult.setTotal(total);
		if (total == 0) {
			return pageResult;
		}
		query.with(Sort.by(Sort.Direction.DESC, "createTime"))
				.skip((long) (pageNo - 1) * pageSize)
				.limit(pageSize);
		List<UserCollect> collectList = mongoTemplate.find(query, UserCollect.class, collectionName);
		if (CollectionUtils.isEmpty(collectList)) {
			return pageResult;
		}

		//2.批量补声音信息
		List<Long> trackIdList = collectList.stream()
				.map(UserCollect::getTrackId).filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<TrackInfo> trackInfoList = this.getData(albumFeignClient.findTrackInfoByIds(trackIdList));
		Map<Long, TrackInfo> trackInfoMap = new HashMap<>();
		if (!CollectionUtils.isEmpty(trackInfoList)) {
			trackInfoList.forEach(trackInfo -> trackInfoMap.put(trackInfo.getId(), trackInfo));
		}

		//3.批量补专辑标题：声音表里只有专辑ID，列表项要显示专辑名
		List<Long> albumIdList = trackInfoList == null ? Collections.emptyList()
				: trackInfoList.stream().map(TrackInfo::getAlbumId).filter(Objects::nonNull)
				.collect(Collectors.toList());
		List<AlbumInfo> albumInfoList = this.getData(albumFeignClient.findAlbumInfoByIds(albumIdList));
		Map<Long, AlbumInfo> albumInfoMap = new HashMap<>();
		if (!CollectionUtils.isEmpty(albumInfoList)) {
			albumInfoList.forEach(albumInfo -> albumInfoMap.put(albumInfo.getId(), albumInfo));
		}

		//4.组装：声音被下架时只保留收藏时间，那一条会显示成空白，不会报错
		List<UserCollectVo> records = new ArrayList<>();
		for (UserCollect userCollect : collectList) {
			UserCollectVo collectVo = new UserCollectVo();
			collectVo.setTrackId(userCollect.getTrackId());
			collectVo.setCreateTime(userCollect.getCreateTime());
			TrackInfo trackInfo = trackInfoMap.get(userCollect.getTrackId());
			if (trackInfo != null) {
				collectVo.setAlbumId(trackInfo.getAlbumId());
				collectVo.setTrackTitle(trackInfo.getTrackTitle());
				collectVo.setMediaDuration(trackInfo.getMediaDuration());
				//声音自带封面优先，没有就用专辑封面
				collectVo.setCoverUrl(trackInfo.getCoverUrl());
				AlbumInfo albumInfo = albumInfoMap.get(trackInfo.getAlbumId());
				if (albumInfo != null) {
					collectVo.setAlbumTitle(albumInfo.getAlbumTitle());
					if (!StringUtils.hasText(collectVo.getCoverUrl())) {
						collectVo.setCoverUrl(albumInfo.getCoverUrl());
					}
				}
			}
			records.add(collectVo);
		}
		pageResult.setRecords(records);
		return pageResult;
	}


	/**
	 * @param userId 用户ID
	 * @return 收藏集合名：userCollect_{userId%100}
	 */
	private String collectionName(Long userId) {
		return MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_COLLECT, userId);
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
