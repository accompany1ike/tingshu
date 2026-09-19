package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class TrackInfoServiceImpl extends ServiceImpl<TrackInfoMapper, TrackInfo> implements TrackInfoService {

	@Autowired
	private TrackInfoMapper trackInfoMapper;

	@Autowired
	private TrackStatMapper trackStatMapper;

	@Autowired
	private AlbumInfoMapper albumInfoMapper;

	@Autowired
	private VodService vodService;


	/**
	 * 保存声音（新增）
	 *
	 * @param userId      当前登录用户ID
	 * @param trackInfoVo 声音信息
	 * @return 新增声音ID
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long saveTrackInfo(Long userId, TrackInfoVo trackInfoVo) {
		//1.校验所属专辑：必须是真实存在且属于当前用户自己的专辑
		AlbumInfo albumInfo = this.checkAlbum(userId, trackInfoVo.getAlbumId());

		//2.组装声音实体：客户端提交的字段做必要兜底，服务端托管的字段一律自己赋值
		TrackInfo trackInfo = new TrackInfo();
		trackInfo.setTrackTitle(trackInfoVo.getTrackTitle().trim());
		trackInfo.setTrackIntro(trackInfoVo.getTrackIntro());
		trackInfo.setTrackRichIntro(trackInfoVo.getTrackRichIntro());
		//没传封面就用专辑封面，声音列表里不至于出现空白图
		trackInfo.setCoverUrl(StringUtils.hasText(trackInfoVo.getCoverUrl())
				? trackInfoVo.getCoverUrl() : albumInfo.getCoverUrl());
		trackInfo.setMediaFileId(trackInfoVo.getMediaFileId());
		trackInfo.setMediaUrl(trackInfoVo.getMediaUrl());

		trackInfo.setUserId(userId);
		trackInfo.setAlbumId(albumInfo.getId());
		//排序值从1开始依次递增，取当前专辑里最大的排序值加一，新声音排在最后
		trackInfo.setOrderNum(this.nextOrderNum(albumInfo.getId()));
		trackInfo.setSource(SystemConstant.TRACK_SOURCE_USER);
		//内容审核机制暂未实现，先置为审核通过，后续接入审核后改为待审核
		trackInfo.setStatus(SystemConstant.TRACK_STATUS_PASS);
		trackInfo.setIsOpen(StringUtils.hasText(trackInfoVo.getIsOpen()) ? trackInfoVo.getIsOpen() : "1");
		//媒体类型表单里没有，从播放地址的后缀取
		trackInfo.setMediaType(this.getMediaType(trackInfoVo.getMediaUrl()));
		//时长、文件大小表单里也没有，去点播查一次（查不到就用库里的默认值，不能因此挡住保存）
		this.fillMediaInfo(trackInfo, trackInfoVo.getMediaFileId());

		trackInfoMapper.insert(trackInfo);
		Long trackId = trackInfo.getId();
		log.info("新增声音成功，声音ID：{}，专辑ID：{}，用户ID：{}", trackId, albumInfo.getId(), userId);

		//3.初始化声音统计信息：播放量、收藏量、点赞量、评论数
		this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_PLAY);
		this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_COLLECT);
		this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_PRAISE);
		this.saveTrackStat(trackId, SystemConstant.TRACK_STAT_COMMENT);

		//4.专辑的声音数量加一（专辑卡片上直接显示这个数）
		this.updateIncludeTrackCount(albumInfo, 1);

		return trackId;
	}


	/**
	 * 查询声音详情（编辑回显用）
	 *
	 * @param userId  当前登录用户ID
	 * @param trackId 声音ID
	 * @return 声音详情
	 */
	@Override
	public TrackInfo getTrackInfo(Long userId, Long trackId) {
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		if (trackInfo == null || !userId.equals(trackInfo.getUserId())) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "声音不存在或无权查看");
		}
		return trackInfo;
	}


	/**
	 * 编辑声音
	 * <p>
	 * 只更新表单里能改的字段；null 字段 MyBatis-Plus 会忽略，不会把库里原有值覆盖成空
	 *
	 * @param userId      当前登录用户ID
	 * @param trackId     声音ID
	 * @param trackInfoVo 声音信息
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateTrackInfo(Long userId, Long trackId, TrackInfoVo trackInfoVo) {
		//1.校验：声音必须存在，且属于当前用户
		TrackInfo existTrack = trackInfoMapper.selectById(trackId);
		if (existTrack == null || !userId.equals(existTrack.getUserId())) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "声音不存在或无权修改");
		}

		//2.组装要更新的字段：所属专辑、排序值、来源、状态都不允许改（换专辑会让排序值错乱）
		TrackInfo trackInfo = new TrackInfo();
		trackInfo.setId(trackId);
		trackInfo.setTrackTitle(StringUtils.hasText(trackInfoVo.getTrackTitle())
				? trackInfoVo.getTrackTitle().trim() : null);
		trackInfo.setTrackIntro(trackInfoVo.getTrackIntro());
		trackInfo.setTrackRichIntro(trackInfoVo.getTrackRichIntro());
		trackInfo.setCoverUrl(trackInfoVo.getCoverUrl());
		trackInfo.setIsOpen(trackInfoVo.getIsOpen());

		//3.编辑页如果重新上传了音频，媒体信息一起换掉，时长和大小要重新去点播查
		if (StringUtils.hasText(trackInfoVo.getMediaFileId())
				&& !trackInfoVo.getMediaFileId().equals(existTrack.getMediaFileId())) {
			trackInfo.setMediaFileId(trackInfoVo.getMediaFileId());
			trackInfo.setMediaUrl(trackInfoVo.getMediaUrl());
			trackInfo.setMediaType(this.getMediaType(trackInfoVo.getMediaUrl()));
			this.fillMediaInfo(trackInfo, trackInfoVo.getMediaFileId());
		}

		trackInfoMapper.updateById(trackInfo);
		log.info("编辑声音成功，声音ID：{}，操作人：{}", trackId, userId);
	}


	/**
	 * 删除声音
	 * <p>
	 * 逻辑删除（@TableLogic 走 is_deleted 更新，不做物理删除），
	 * 同时把声音统计一起删掉，避免留下孤儿记录
	 *
	 * @param userId  当前登录用户ID
	 * @param trackId 声音ID
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeTrackInfo(Long userId, Long trackId) {
		//1.校验：声音必须存在，且属于当前用户
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		if (trackInfo == null || !userId.equals(trackInfo.getUserId())) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "声音不存在或无权删除");
		}

		//2.删除声音
		trackInfoMapper.deleteById(trackId);

		//3.连带删除声音统计数据
		trackStatMapper.delete(new LambdaQueryWrapper<TrackStat>()
				.eq(TrackStat::getTrackId, trackId));

		//4.专辑的声音数量减一
		AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfo.getAlbumId());
		if (albumInfo != null) {
			this.updateIncludeTrackCount(albumInfo, -1);
		}
		log.info("删除声音成功，声音ID：{}，操作人：{}", trackId, userId);
	}


	/**
	 * 分页查询当前登录用户的声音列表
	 * <p>
	 * 最新添加的排在最前面：声音ID是自增的，按ID倒序等价于按创建时间倒序
	 *
	 * @param page           页码
	 * @param limit          每页条数
	 * @param userId         当前登录用户ID
	 * @param trackInfoQuery 查询条件（声音状态、标题关键字）
	 * @return 分页结果
	 */
	@Override
	public Page<TrackListVo> findUserTrackPage(int page, int limit, Long userId, TrackInfoQuery trackInfoQuery) {
		TrackInfoQuery query = trackInfoQuery == null ? new TrackInfoQuery() : trackInfoQuery;

		//1.分页查询声音：只查当前登录用户的（不信任请求参数里的userId），支持按状态过滤和标题模糊查询
		LambdaQueryWrapper<TrackInfo> queryWrapper = new LambdaQueryWrapper<TrackInfo>()
				.eq(TrackInfo::getUserId, userId)
				.eq(StringUtils.hasText(query.getStatus()), TrackInfo::getStatus, query.getStatus())
				.like(StringUtils.hasText(query.getTrackTitle()), TrackInfo::getTrackTitle, query.getTrackTitle())
				.orderByDesc(TrackInfo::getId);
		Page<TrackInfo> trackPage = trackInfoMapper.selectPage(new Page<>(page, limit), queryWrapper);

		//2.转成页面需要的VO
		List<TrackListVo> voList = trackPage.getRecords().stream().map(trackInfo -> {
			TrackListVo trackListVo = new TrackListVo();
			trackListVo.setTrackId(trackInfo.getId());
			trackListVo.setTrackTitle(trackInfo.getTrackTitle());
			trackListVo.setAlbumId(trackInfo.getAlbumId());
			trackListVo.setCoverUrl(trackInfo.getCoverUrl());
			trackListVo.setMediaDuration(trackInfo.getMediaDuration());
			trackListVo.setStatus(trackInfo.getStatus());
			trackListVo.setPlayStatNum(0);
			trackListVo.setCollectStatNum(0);
			trackListVo.setPraiseStatNum(0);
			trackListVo.setCommentStatNum(0);
			return trackListVo;
		}).collect(Collectors.toList());

		Page<TrackListVo> result = new Page<>(trackPage.getCurrent(), trackPage.getSize(), trackPage.getTotal());
		result.setRecords(voList);
		if (CollectionUtils.isEmpty(voList)) {
			return result;
		}

		//3.一次性查出本页声音所属的专辑标题和统计数据再回填，避免每条声音查一次库
		Map<Long, String> albumTitleMap = albumInfoMapper.selectList(new LambdaQueryWrapper<AlbumInfo>()
						.select(AlbumInfo::getId, AlbumInfo::getAlbumTitle)
						.in(AlbumInfo::getId, voList.stream().map(TrackListVo::getAlbumId)
								.distinct().collect(Collectors.toList())))
				.stream().collect(Collectors.toMap(AlbumInfo::getId, AlbumInfo::getAlbumTitle, (a, b) -> a));
		Map<Long, TrackListVo> trackListVoMap = voList.stream()
				.collect(Collectors.toMap(TrackListVo::getTrackId, Function.identity()));

		List<TrackStat> trackStatList = trackStatMapper.selectList(new LambdaQueryWrapper<TrackStat>()
				.in(TrackStat::getTrackId, trackListVoMap.keySet()));
		for (TrackStat trackStat : trackStatList) {
			TrackListVo trackListVo = trackListVoMap.get(trackStat.getTrackId());
			if (trackListVo == null || trackStat.getStatType() == null) {
				continue;
			}
			switch (trackStat.getStatType()) {
				case SystemConstant.TRACK_STAT_PLAY -> trackListVo.setPlayStatNum(trackStat.getStatNum());
				case SystemConstant.TRACK_STAT_COLLECT -> trackListVo.setCollectStatNum(trackStat.getStatNum());
				case SystemConstant.TRACK_STAT_PRAISE -> trackListVo.setPraiseStatNum(trackStat.getStatNum());
				case SystemConstant.TRACK_STAT_COMMENT -> trackListVo.setCommentStatNum(trackStat.getStatNum());
				default -> log.warn("未知的声音统计类型：{}，声音ID：{}", trackStat.getStatType(), trackStat.getTrackId());
			}
		}
		voList.forEach(trackListVo -> trackListVo.setAlbumTitle(albumTitleMap.get(trackListVo.getAlbumId())));
		return result;
	}


	/**
	 * 校验所属专辑：必须存在，且属于当前登录用户
	 *
	 * @param userId  当前登录用户ID
	 * @param albumId 专辑ID
	 * @return 专辑实体
	 */
	private AlbumInfo checkAlbum(Long userId, Long albumId) {
		if (albumId == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "请选择所属专辑");
		}
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		if (albumInfo == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "所属专辑不存在，请重新选择");
		}
		if (!userId.equals(albumInfo.getUserId())) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "只能给自己创建的专辑添加声音");
		}
		return albumInfo;
	}


	/**
	 * 取当前专辑的下一个排序值：专辑里没有声音时从1开始
	 *
	 * @param albumId 专辑ID
	 * @return 排序值
	 */
	private Integer nextOrderNum(Long albumId) {
		TrackInfo lastTrack = trackInfoMapper.selectOne(new LambdaQueryWrapper<TrackInfo>()
				.eq(TrackInfo::getAlbumId, albumId)
				.orderByDesc(TrackInfo::getOrderNum)
				.last("limit 1"));
		return lastTrack == null || lastTrack.getOrderNum() == null ? 1 : lastTrack.getOrderNum() + 1;
	}


	/**
	 * 回填媒体时长与文件大小
	 * <p>
	 * 表单里没有这两个字段，去点播查一次；查不到就保持默认值，不能因为查不到就让保存失败
	 *
	 * @param trackInfo   声音实体
	 * @param mediaFileId 点播文件唯一标识
	 */
	private void fillMediaInfo(TrackInfo trackInfo, String mediaFileId) {
		if (!StringUtils.hasText(mediaFileId)) {
			return;
		}
		Map<String, Object> mediaInfo = vodService.getMediaInfo(mediaFileId);
		if (mediaInfo.get("mediaDuration") instanceof BigDecimal mediaDuration) {
			trackInfo.setMediaDuration(mediaDuration);
		}
		if (mediaInfo.get("mediaSize") instanceof Long mediaSize) {
			trackInfo.setMediaSize(mediaSize);
		}
	}


	/**
	 * 从播放地址里取媒体类型（track_info.media_type 最长10个字符）
	 *
	 * @param mediaUrl 播放地址
	 * @return 小写后缀，例如 mp3，取不到返回空串
	 */
	private String getMediaType(String mediaUrl) {
		if (!StringUtils.hasText(mediaUrl)) {
			return "";
		}
		String value = mediaUrl;
		int queryIndex = value.indexOf('?');
		if (queryIndex > -1) {
			value = value.substring(0, queryIndex);
		}
		String suffix = StringUtils.getFilenameExtension(value);
		if (!StringUtils.hasText(suffix)) {
			return "";
		}
		suffix = suffix.toLowerCase(Locale.ROOT);
		return suffix.length() > 10 ? "" : suffix;
	}


	/**
	 * 初始化一条声音统计记录
	 *
	 * @param trackId  声音ID
	 * @param statType 统计类型：0701-播放量 0702-收藏量 0703-点赞量 0704-评论数
	 */
	private void saveTrackStat(Long trackId, String statType) {
		TrackStat trackStat = new TrackStat();
		trackStat.setTrackId(trackId);
		trackStat.setStatType(statType);
		trackStat.setStatNum(0);
		trackStatMapper.insert(trackStat);
	}


	/**
	 * 同步专辑的声音数量
	 *
	 * @param albumInfo 专辑实体（需要带 id 与当前声音数量）
	 * @param delta     增量：新增声音传 1，删除声音传 -1
	 */
	private void updateIncludeTrackCount(AlbumInfo albumInfo, int delta) {
		int includeTrackCount = albumInfo.getIncludeTrackCount() == null ? 0 : albumInfo.getIncludeTrackCount();
		AlbumInfo updateAlbum = new AlbumInfo();
		updateAlbum.setId(albumInfo.getId());
		updateAlbum.setIncludeTrackCount(Math.max(0, includeTrackCount + delta));
		albumInfoMapper.updateById(updateAlbum);
	}

}
