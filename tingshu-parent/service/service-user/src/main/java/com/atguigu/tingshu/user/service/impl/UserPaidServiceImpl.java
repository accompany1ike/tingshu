package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.model.user.UserVipService;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.user.mapper.UserVipServiceMapper;
import com.atguigu.tingshu.user.service.UserPaidService;
import com.atguigu.tingshu.user.service.VipServiceConfigService;
import com.atguigu.tingshu.vo.user.UserPayRecordMqVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * 订单支付成功后的「发货」
 * <p>
 * 已购记录是专辑模块判断「这条声音还要不要显示付费标识」的依据，
 * 少写一条用户就会重复付费，所以这里必须幂等：按订单号去重，重复消息直接跳过
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserPaidServiceImpl implements UserPaidService {

	@Autowired
	private UserPaidAlbumMapper userPaidAlbumMapper;

	@Autowired
	private UserPaidTrackMapper userPaidTrackMapper;

	@Autowired
	private UserVipServiceMapper userVipServiceMapper;

	@Autowired
	private UserInfoMapper userInfoMapper;

	@Autowired
	private VipServiceConfigService vipServiceConfigService;

	@Autowired
	private AlbumFeignClient albumFeignClient;


	/**
	 * 订单支付成功后的「发货」：写已购记录、开通会员
	 *
	 * @param userPayRecordMqVo 支付成功消息
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void savePaidRecord(UserPayRecordMqVo userPayRecordMqVo) {
		if (userPayRecordMqVo == null || userPayRecordMqVo.getUserId() == null
				|| !StringUtils.hasText(userPayRecordMqVo.getOrderNo())
				|| !StringUtils.hasText(userPayRecordMqVo.getItemType())
				|| userPayRecordMqVo.getItemId() == null) {
			log.error("支付成功消息不完整，无法发货：{}", userPayRecordMqVo);
			return;
		}
		String itemType = userPayRecordMqVo.getItemType();
		if (SystemConstant.ORDER_ITEM_TYPE_ALBUM.equals(itemType)) {
			this.savePaidAlbum(userPayRecordMqVo);
		} else if (SystemConstant.ORDER_ITEM_TYPE_TRACK.equals(itemType)) {
			this.savePaidTrack(userPayRecordMqVo);
		} else if (SystemConstant.ORDER_ITEM_TYPE_VIP.equals(itemType)) {
			this.openVip(userPayRecordMqVo);
		} else {
			log.error("不支持的付款项目类型，无法发货，订单号：{}，类型：{}",
					userPayRecordMqVo.getOrderNo(), itemType);
		}
	}


	/**
	 * 已购整张专辑
	 *
	 * @param userPayRecordMqVo 支付成功消息，itemId 为专辑ID
	 */
	private void savePaidAlbum(UserPayRecordMqVo userPayRecordMqVo) {
		if (this.existsPaidAlbum(userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId())) {
			log.info("已购专辑记录已存在，跳过，订单号：{}", userPayRecordMqVo.getOrderNo());
			return;
		}
		UserPaidAlbum userPaidAlbum = new UserPaidAlbum();
		userPaidAlbum.setOrderNo(userPayRecordMqVo.getOrderNo());
		userPaidAlbum.setUserId(userPayRecordMqVo.getUserId());
		userPaidAlbum.setAlbumId(userPayRecordMqVo.getItemId());
		userPaidAlbumMapper.insert(userPaidAlbum);
		log.info("已购专辑记录写入成功，订单号：{}，用户ID：{}，专辑ID：{}",
				userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId(), userPayRecordMqVo.getItemId());
	}


	/**
	 * 已购单集声音
	 *
	 * @param userPayRecordMqVo 支付成功消息，itemId 为声音ID
	 */
	private void savePaidTrack(UserPayRecordMqVo userPayRecordMqVo) {
		if (this.existsPaidTrack(userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId())) {
			log.info("已购声音记录已存在，跳过，订单号：{}", userPayRecordMqVo.getOrderNo());
			return;
		}
		//已购声音表带专辑ID，回源专辑模块补上，方便按专辑维度判断整张是否买过
		Long albumId = null;
		List<TrackInfo> trackInfoList = this.getData(albumFeignClient.findTrackInfoByIds(List.of(userPayRecordMqVo.getItemId())));
		if (!CollectionUtils.isEmpty(trackInfoList)) {
			albumId = trackInfoList.get(0).getAlbumId();
		}
		UserPaidTrack userPaidTrack = new UserPaidTrack();
		userPaidTrack.setOrderNo(userPayRecordMqVo.getOrderNo());
		userPaidTrack.setUserId(userPayRecordMqVo.getUserId());
		userPaidTrack.setAlbumId(albumId);
		userPaidTrack.setTrackId(userPayRecordMqVo.getItemId());
		userPaidTrackMapper.insert(userPaidTrack);
		log.info("已购声音记录写入成功，订单号：{}，用户ID：{}，声音ID：{}，专辑ID：{}",
				userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId(),
				userPayRecordMqVo.getItemId(), albumId);
	}


	/**
	 * 开通 / 续费会员
	 * <p>
	 * 续费要在原到期时间上顺延，而不是从当前时间重新算，
	 * 否则用户提前续费会白丢剩余的会员时长
	 *
	 * @param userPayRecordMqVo 支付成功消息，itemId 为VIP服务配置ID
	 */
	private void openVip(UserPayRecordMqVo userPayRecordMqVo) {
		if (this.existsVipService(userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId())) {
			log.info("会员开通记录已存在，跳过，订单号：{}", userPayRecordMqVo.getOrderNo());
			return;
		}
		VipServiceConfig vipServiceConfig = vipServiceConfigService.getById(userPayRecordMqVo.getItemId());
		if (vipServiceConfig == null) {
			log.error("会员服务配置不存在，无法开通，订单号：{}，配置ID：{}",
					userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getItemId());
			return;
		}
		int serviceMonth = vipServiceConfig.getServiceMonth() == null ? 1 : vipServiceConfig.getServiceMonth();

		//1.算生效与到期时间：还在会员期内就在原到期时间上顺延
		LocalDateTime now = LocalDateTime.now();
		UserInfo userInfo = userInfoMapper.selectById(userPayRecordMqVo.getUserId());
		LocalDateTime startTime = now;
		LocalDateTime baseTime = now;
		if (userInfo != null && userInfo.getVipExpireTime() != null
				&& userInfo.getVipExpireTime().after(new Date())) {
			baseTime = this.toLocalDateTime(userInfo.getVipExpireTime());
		}
		LocalDateTime expireTime = baseTime.plusMonths(serviceMonth);

		//2.落会员开通记录
		UserVipService userVipService = new UserVipService();
		userVipService.setOrderNo(userPayRecordMqVo.getOrderNo());
		userVipService.setUserId(userPayRecordMqVo.getUserId());
		userVipService.setStartTime(this.toDate(startTime));
		userVipService.setExpireTime(this.toDate(expireTime));
		userVipService.setIsAutoRenew(0);
		userVipServiceMapper.insert(userVipService);

		//3.刷新用户身上的会员状态：只更新这两个字段，其它字段不动
		UserInfo updateUserInfo = new UserInfo();
		updateUserInfo.setId(userPayRecordMqVo.getUserId());
		updateUserInfo.setIsVip(1);
		updateUserInfo.setVipExpireTime(this.toDate(expireTime));
		userInfoMapper.updateById(updateUserInfo);
		log.info("会员开通成功，订单号：{}，用户ID：{}，服务月数：{}，到期时间：{}",
				userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId(), serviceMonth, expireTime);
	}


	private boolean existsPaidAlbum(String orderNo, Long userId) {
		return userPaidAlbumMapper.exists(new LambdaQueryWrapper<UserPaidAlbum>()
				.eq(UserPaidAlbum::getOrderNo, orderNo)
				.eq(UserPaidAlbum::getUserId, userId));
	}


	private boolean existsPaidTrack(String orderNo, Long userId) {
		return userPaidTrackMapper.exists(new LambdaQueryWrapper<UserPaidTrack>()
				.eq(UserPaidTrack::getOrderNo, orderNo)
				.eq(UserPaidTrack::getUserId, userId));
	}


	private boolean existsVipService(String orderNo, Long userId) {
		return userVipServiceMapper.exists(new LambdaQueryWrapper<UserVipService>()
				.eq(UserVipService::getOrderNo, orderNo)
				.eq(UserVipService::getUserId, userId));
	}


	private LocalDateTime toLocalDateTime(Date date) {
		return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
	}


	private Date toDate(LocalDateTime localDateTime) {
		return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
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
