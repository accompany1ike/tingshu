package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.mq.AlbumIndexMqSender;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackPaidSettingVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.album.TrackStatVo;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

	@Autowired
	private AlbumIndexMqSender albumIndexMqSender;

	@Autowired
	private UserFeignClient userFeignClient;


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
	 * 根据专辑ID分页查询声音列表（专辑详情页用）
	 * <p>
	 * 声音本身的数据一次 SQL 查完，付费标识要结合专辑付费类型和当前用户的"已获得"状态逐条算，
	 * 所以整页只查一次专辑、最多远程查一次用户侧数据，不做 N+1 查询
	 *
	 * @param albumId 专辑ID
	 * @param page    页码
	 * @param limit   每页条数
	 * @param userId  当前登录用户ID，未登录传 null
	 * @return 分页结果（records 含付费标识）
	 */
	@Override
	public Page<AlbumTrackListVo> findAlbumTrackPage(Long albumId, int page, int limit, Long userId) {
		//1.分页查询声音基础信息（含播放量、评论数），按集数正序
		Page<AlbumTrackListVo> pageParam = trackInfoMapper.selectAlbumTrackPage(new Page<>(page, limit), albumId);
		List<AlbumTrackListVo> records = pageParam.getRecords();
		if (CollectionUtils.isEmpty(records)) {
			return pageParam;
		}

		//2.查专辑拿付费规则
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		if (albumInfo == null) {
			//理论上走不到：专辑详情页会先查专辑。真出现孤儿数据时按"需要付费"处理，宁可多拦不可错放
			log.warn("查询专辑声音列表时专辑不存在，已按需要付费处理，专辑ID：{}", albumId);
			records.forEach(track -> track.setIsShowPaidMark(true));
			return pageParam;
		}

		//3.按付费类型准备判定依据：只对真正需要用户信息的类型发起远程调用
		String payType = albumInfo.getPayType();
		boolean isVip = false;
		Set<Long> paidTrackIdSet = Collections.emptySet();
		if (SystemConstant.ALBUM_PAY_TYPE_VIPFREE.equals(payType)) {
			isVip = this.isVipUser(userId);
		} else if (SystemConstant.ALBUM_PAY_TYPE_REQUIRE.equals(payType)) {
			paidTrackIdSet = this.getPaidTrackIdSet(userId, records);
		}

		//4.逐条判断是否显示付费标识
		for (AlbumTrackListVo track : records) {
			track.setIsShowPaidMark(this.needShowPaidMark(track, albumInfo, isVip, paidTrackIdSet));
		}
		return pageParam;
	}


	/**
	 * 更新声音统计信息（MQ 消费者调用）
	 * <p>
	 * 播放量、收藏量、点赞量、评论数的变更都走这一个入口：
	 * 先按「声音ID + 统计类型」原子累加，影响行数为 0 说明这一行不存在（老数据或初始化遗漏），
	 * 再补插一条，避免统计凭空丢失
	 *
	 * @param trackStatMqVo 统计变更消息（专辑ID、声音ID、统计类型、增量）
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateTrackStat(TrackStatMqVo trackStatMqVo) {
		//1.校验：声音ID与统计类型缺一不可，增量为 0 时无需更新
		if (trackStatMqVo == null || trackStatMqVo.getTrackId() == null
				|| !StringUtils.hasText(trackStatMqVo.getStatType())) {
			log.warn("声音统计消息内容不完整，已忽略：{}", trackStatMqVo);
			return;
		}
		int count = trackStatMqVo.getCount() == null ? 0 : trackStatMqVo.getCount();
		if (count == 0) {
			return;
		}

		//2.原子累加：并发下由数据库保证计数不丢
		int rows = trackStatMapper.increaseStatNum(trackStatMqVo.getTrackId(), trackStatMqVo.getStatType(), count);
		if (rows > 0) {
			log.info("声音统计更新成功，声音ID：{}，统计类型：{}，增量：{}",
					trackStatMqVo.getTrackId(), trackStatMqVo.getStatType(), count);
			return;
		}

		//3.统计行不存在时补插一条：直接按增量作为初始值
		TrackStat trackStat = new TrackStat();
		trackStat.setTrackId(trackStatMqVo.getTrackId());
		trackStat.setStatType(trackStatMqVo.getStatType());
		trackStat.setStatNum(count);
		trackStatMapper.insert(trackStat);
		log.warn("声音统计行缺失，已补插，声音ID：{}，统计类型：{}，初始值：{}",
				trackStatMqVo.getTrackId(), trackStatMqVo.getStatType(), count);
	}


	/**
	 * 查询声音统计信息（播放页用）
	 * <p>
	 * 与 getAlbumStatVo 同样的思路：track_stat 是纵向存储，查出来后按 stat_type 做一次行转列
	 *
	 * @param trackId 声音ID
	 * @return 声音统计信息，四项统计都查不到时各项为 0
	 */
	@Override
	public TrackStatVo getTrackStatVo(Long trackId) {
		TrackStatVo trackStatVo = new TrackStatVo();
		trackStatVo.setPlayStatNum(0);
		trackStatVo.setCollectStatNum(0);
		trackStatVo.setPraiseStatNum(0);
		trackStatVo.setCommentStatNum(0);
		trackStatVo.setAlbumCommentStatNum(0);
		if (trackId == null) {
			return trackStatVo;
		}

		List<TrackStat> trackStatList = trackStatMapper.selectList(new LambdaQueryWrapper<TrackStat>()
				.eq(TrackStat::getTrackId, trackId));
		for (TrackStat trackStat : trackStatList) {
			if (trackStat.getStatType() == null) {
				continue;
			}
			int statNum = trackStat.getStatNum() == null ? 0 : trackStat.getStatNum();
			switch (trackStat.getStatType()) {
				case SystemConstant.TRACK_STAT_PLAY -> trackStatVo.setPlayStatNum(statNum);
				case SystemConstant.TRACK_STAT_COLLECT -> trackStatVo.setCollectStatNum(statNum);
				case SystemConstant.TRACK_STAT_PRAISE -> trackStatVo.setPraiseStatNum(statNum);
				//0704 是声音自己的评论数；albumCommentStatNum 是历史遗留字段名，填同值保持兼容
				case SystemConstant.TRACK_STAT_COMMENT -> {
					trackStatVo.setCommentStatNum(statNum);
					trackStatVo.setAlbumCommentStatNum(statNum);
				}
				default -> log.warn("未知的声音统计类型：{}，声音ID：{}", trackStat.getStatType(), trackId);
			}
		}
		return trackStatVo;
	}


	/**
	 * 判断单条声音是否需要显示付费标识
	 * <p>
	 * 规则按优先级从高到低：
	 * 1.免费专辑：整张专辑免费，不显示；
	 * 2.免费试听：集数在专辑允许免费试听的范围内，任何人都不用付费，不显示；
	 * 3.VIP免费专辑：当前用户是有效VIP就不显示，否则显示；
	 * 4.付费专辑：当前用户已经买过这条声音就不显示，否则显示；
	 * 5.兜底：付费类型未知时按需要付费处理，避免把付费内容当免费放出去
	 *
	 * @param track          声音
	 * @param albumInfo      所属专辑
	 * @param isVip          当前用户是否为有效VIP
	 * @param paidTrackIdSet 当前用户在本页声音中已购买的声音ID集合
	 * @return true-需要显示付费标识
	 */
	private boolean needShowPaidMark(AlbumTrackListVo track, AlbumInfo albumInfo,
									 boolean isVip, Set<Long> paidTrackIdSet) {
		String payType = albumInfo.getPayType();

		//1.免费专辑：全部不显示付费标识
		if (SystemConstant.ALBUM_PAY_TYPE_FREE.equals(payType)) {
			return false;
		}

		//2.免费试听：前 N 集任何人都能直接听（N 为空或 0 表示不提供免费试听）
		Integer tracksForFree = albumInfo.getTracksForFree();
		if (tracksForFree != null && tracksForFree > 0
				&& track.getOrderNum() != null && track.getOrderNum() <= tracksForFree) {
			return false;
		}

		//3.VIP免费专辑：VIP会员免费
		if (SystemConstant.ALBUM_PAY_TYPE_VIPFREE.equals(payType)) {
			return !isVip;
		}

		//4.付费专辑：买过的声音不用再付费
		if (SystemConstant.ALBUM_PAY_TYPE_REQUIRE.equals(payType)) {
			return !paidTrackIdSet.contains(track.getTrackId());
		}

		//5.未知付费类型
		log.warn("未知的专辑付费类型：{}，专辑ID：{}", payType, albumInfo.getId());
		return true;
	}


	/**
	 * 判断用户是否为有效VIP会员（远程调用用户模块）
	 *
	 * @param userId 用户ID，未登录传 null
	 * @return true-有效VIP；未登录、调用失败都返回 false
	 */
	private boolean isVipUser(Long userId) {
		if (userId == null) {
			return false;
		}
		Result<Boolean> result = userFeignClient.getUserIsVip(userId);
		return result != null && Boolean.TRUE.equals(result.getData());
	}


	/**
	 * 查出本页声音里当前用户已经购买过的声音ID
	 *
	 * @param userId  用户ID，未登录传 null
	 * @param records 本页声音
	 * @return 已购买的声音ID集合；未登录或没有购买记录时返回空集合
	 */
	private Set<Long> getPaidTrackIdSet(Long userId, List<AlbumTrackListVo> records) {
		if (userId == null) {
			return Collections.emptySet();
		}
		List<Long> trackIdList = records.stream()
				.map(AlbumTrackListVo::getTrackId)
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		if (CollectionUtils.isEmpty(trackIdList)) {
			return Collections.emptySet();
		}
		Result<List<Long>> result = userFeignClient.getUserPaidTrackIdList(userId, trackIdList);
		List<Long> paidTrackIdList = result == null ? null : result.getData();
		return CollectionUtils.isEmpty(paidTrackIdList) ? Collections.emptySet() : new HashSet<>(paidTrackIdList);
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
	 * <p>
	 * 专辑卡片上显示的就是这个集数，索引库里的 includeTrackCount 也要跟着变，
	 * 所以更新完库里的数量后再通知搜索模块重建一次索引
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

		//声音数量变了，索引里的集数也得刷新
		albumIndexMqSender.sendUpperAlbum(albumInfo.getId());
	}


	/**
	 * 根据声音ID集合批量查询声音（服务内部调用）
	 * <p>
	 * 入参可能来自用户收藏/播放历史里的一串 ID，先去重再去查；
	 * 已下架或已删除的声音查不出来，调用方按「缺了就是没了」处理
	 *
	 * @param trackIdList 声音ID集合
	 * @return 声音集合
	 */
	@Override
	public List<TrackInfo> findTrackInfoByIds(List<Long> trackIdList) {
		if (CollectionUtils.isEmpty(trackIdList)) {
			return Collections.emptyList();
		}
		List<Long> distinctIds = trackIdList.stream()
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		if (CollectionUtils.isEmpty(distinctIds)) {
			return Collections.emptyList();
		}
		return trackInfoMapper.selectList(new LambdaQueryWrapper<TrackInfo>()
				.in(TrackInfo::getId, distinctIds));
	}


	/**
	 * 查询某个声音的购买选项（详情页「购买本集」弹窗用）
	 * <p>
	 * 价格全部从专辑上的单价现算，不另存一份声音价格：
	 * 专辑价格类型为「单集」时 price 就是每集单价，整专辑购买 = 单价 × 总集数
	 *
	 * @param trackId 声音ID
	 * @return 购买选项列表；专辑未定价时返回空列表（前端弹窗为空，不报错）
	 */
	@Override
	public List<TrackPaidSettingVo> findUserTrackPaidList(Long trackId) {
		if (trackId == null) {
			return Collections.emptyList();
		}
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		if (trackInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "声音不存在");
		}
		AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfo.getAlbumId());
		if (albumInfo == null || albumInfo.getPrice() == null
				|| albumInfo.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
			log.info("专辑未定价，不提供购买选项，专辑ID：{}", trackInfo.getAlbumId());
			return Collections.emptyList();
		}
		BigDecimal unitPrice = albumInfo.getPrice();
		int includeTrackCount = albumInfo.getIncludeTrackCount() == null
				? 1 : Math.max(1, albumInfo.getIncludeTrackCount());

		List<TrackPaidSettingVo> paidSettingList = new ArrayList<>(2);
		//1.单集购买：单价就是一集的价钱
		TrackPaidSettingVo singleSetting = new TrackPaidSettingVo();
		singleSetting.setName("单集购买");
		singleSetting.setPrice(unitPrice);
		singleSetting.setTrackId(trackId);
		singleSetting.setTrackCount(1);
		paidSettingList.add(singleSetting);

		//2.整专辑购买：专辑只有一集时与单集重复，不再展示
		if (includeTrackCount > 1) {
			TrackPaidSettingVo albumSetting = new TrackPaidSettingVo();
			albumSetting.setName("整专辑购买（共" + includeTrackCount + "集）");
			albumSetting.setPrice(unitPrice.multiply(BigDecimal.valueOf(includeTrackCount)));
			albumSetting.setTrackId(trackId);
			albumSetting.setTrackCount(includeTrackCount);
			paidSettingList.add(albumSetting);
		}
		return paidSettingList;
	}

}
