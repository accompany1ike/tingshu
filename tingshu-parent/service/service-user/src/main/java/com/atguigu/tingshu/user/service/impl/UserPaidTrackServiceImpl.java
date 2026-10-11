package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.user.service.UserPaidTrackService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserPaidTrackServiceImpl extends ServiceImpl<UserPaidTrackMapper, UserPaidTrack> implements UserPaidTrackService {

	@Autowired
	private UserPaidAlbumMapper userPaidAlbumMapper;

	/**
	 * 查询用户在指定声音中已购买的声音ID集合
	 * <p>
	 * 未登录（userId 为 null）或没传声音ID时直接返回空集合，不去查库
	 *
	 * @param userId      用户ID
	 * @param trackIdList 待判断的声音ID集合
	 * @return 已购买的声音ID集合
	 */
	@Override
	public List<Long> getUserPaidTrackIdList(Long userId, List<Long> trackIdList) {
		if (userId == null || CollectionUtils.isEmpty(trackIdList)) {
			return Collections.emptyList();
		}
		return this.list(new LambdaQueryWrapper<UserPaidTrack>()
						.select(UserPaidTrack::getTrackId)
						.eq(UserPaidTrack::getUserId, userId)
						.in(UserPaidTrack::getTrackId, trackIdList))
				.stream()
				.map(UserPaidTrack::getTrackId)
				.distinct()
				.collect(Collectors.toList());
	}

}
