package com.atguigu.tingshu.user.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.hutool.core.bean.BeanUtil;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxError;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

	/**
	 * 新用户默认头像（与库中已有用户的默认头像保持一致）
	 */
	private static final String DEFAULT_AVATAR_URL = "https://www.flavorbio.com/uploads/image/20191126/400370101c0e579a7896d5caf5eeffa6.png";

	/**
	 * 新用户默认昵称前缀，后面拼雪花ID保证唯一
	 */
	private static final String NICKNAME_PREFIX = "书友：";

	/**
	 * 昵称、头像长度上限（跟表结构对齐，超长直接给出可读提示而不是抛SQL异常）
	 */
	private static final int NICKNAME_MAX_LENGTH = 100;
	private static final int AVATAR_URL_MAX_LENGTH = 500;

	@Autowired
	private UserInfoMapper userInfoMapper;

	@Autowired
	private WxMaService wxMaService;

	@Autowired
	private RedisTemplate redisTemplate;


	/**
	 * 微信小程序登录
	 *
	 * @param code 微信登录凭证
	 * @return token-访问令牌，userId-用户ID
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Map<String, Object> wxLogin(String code) {
		//1.用code换取openid（微信规定code只能用一次，这里不做重试）
		String openid = this.getOpenidByCode(code);

		//2.按openid查用户，没有就自动注册（wx_open_id 上有唯一索引，天然防止重复注册）
		UserInfo userInfo = userInfoMapper.selectOne(new LambdaQueryWrapper<UserInfo>()
				.eq(UserInfo::getWxOpenId, openid)
				.last("limit 1"));
		if (userInfo == null) {
			userInfo = this.registerUser(openid);
		}

		//3.生成访问令牌，并把用户信息写入Redis登录态（登录切面就是从这里取当前用户）
		String token = UUID.randomUUID().toString().replace("-", "");
		UserInfoVo userInfoVo = new UserInfoVo();
		BeanUtil.copyProperties(userInfo, userInfoVo);
		redisTemplate.opsForValue().set(RedisConstant.USER_LOGIN_KEY_PREFIX + token, userInfoVo,
				RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
		log.info("微信登录成功，用户ID：{}，token：{}", userInfo.getId(), token);

		Map<String, Object> result = new HashMap<>(2);
		result.put("token", token);
		result.put("userId", userInfo.getId());
		return result;
	}

	/**
	 * 获取当前登录用户信息
	 *
	 * @param userId 当前登录用户ID
	 * @return 用户信息
	 */
	@Override
	public UserInfoVo getUserInfo(Long userId) {
		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "用户不存在");
		}
		UserInfoVo userInfoVo = new UserInfoVo();
		BeanUtil.copyProperties(userInfo, userInfoVo);
		return userInfoVo;
	}

	/**
	 * 更新当前登录用户信息（昵称、头像）
	 *
	 * @param userId     当前登录用户ID
	 * @param userInfoVo 待更新的用户信息
	 */
	@Override
	public void updateUserInfo(Long userId, UserInfoVo userInfoVo) {
		if (userInfoVo == null || (!StringUtils.hasText(userInfoVo.getNickname())
				&& !StringUtils.hasText(userInfoVo.getAvatarUrl()))) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "请填写要修改的昵称或头像");
		}
		if (StringUtils.hasText(userInfoVo.getNickname()) && userInfoVo.getNickname().length() > NICKNAME_MAX_LENGTH) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "昵称长度不能超过" + NICKNAME_MAX_LENGTH + "个字符");
		}
		if (StringUtils.hasText(userInfoVo.getAvatarUrl()) && userInfoVo.getAvatarUrl().length() > AVATAR_URL_MAX_LENGTH) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "头像地址过长");
		}

		//只更新昵称和头像：其它字段由服务端维护，不接受客户端提交
		UserInfo userInfo = new UserInfo();
		userInfo.setId(userId);
		userInfo.setNickname(userInfoVo.getNickname());
		userInfo.setAvatarUrl(userInfoVo.getAvatarUrl());
		userInfoMapper.updateById(userInfo);
		log.info("更新用户信息，用户ID：{}，昵称：{}", userId, userInfoVo.getNickname());
	}

	/**
	 * 用微信登录凭证换取openid
	 *
	 * @param code 微信登录凭证
	 * @return openid
	 */
	private String getOpenidByCode(String code) {
		if (!StringUtils.hasText(code)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "缺少微信登录凭证");
		}
		try {
			WxMaJscode2SessionResult sessionResult = wxMaService.jsCode2SessionInfo(code);
			if (sessionResult == null || !StringUtils.hasText(sessionResult.getOpenid())) {
				log.error("微信登录失败，未获取到openid，返回结果：{}", sessionResult);
				throw new GuiguException(ResultCodeEnum.FAIL.getCode(), "微信登录失败，请重试");
			}
			return sessionResult.getOpenid();
		} catch (WxErrorException e) {
			WxError wxError = e.getError();
			Integer errorCode = wxError == null ? null : wxError.getErrorCode();
			log.error("微信登录失败，code：{}，微信错误码：{}，错误信息：{}", code, errorCode,
					wxError == null ? e.getMessage() : wxError.getErrorMsg(), e);
			throw new GuiguException(ResultCodeEnum.FAIL.getCode(), this.getWxLoginErrorMessage(errorCode));
		}
	}

	/**
	 * 把微信返回的错误码翻译成用户能看懂的提示
	 *
	 * @param errorCode 微信错误码
	 * @return 提示信息
	 */
	private String getWxLoginErrorMessage(Integer errorCode) {
		if (errorCode == null) {
			return "微信登录失败，请重试";
		}
		return switch (errorCode) {
			case 40029 -> "登录凭证无效或已过期，请重新登录";
			case 40163 -> "登录凭证已被使用，请重新登录";
			case 45011 -> "操作过于频繁，请稍后再试";
			case 40013 -> "小程序AppID配置有误，请联系管理员";
			case 40125 -> "小程序密钥配置有误，请联系管理员";
			default -> "微信登录失败（错误码：" + errorCode + "）";
		};
	}

	/**
	 * 自动注册新用户
	 *
	 * @param openid 微信openid
	 * @return 新用户
	 */
	private UserInfo registerUser(String openid) {
		UserInfo userInfo = new UserInfo();
		userInfo.setWxOpenId(openid);
		//默认昵称/头像与库中已有用户风格保持一致，用户可在"编辑资料"里自行修改
		userInfo.setNickname(NICKNAME_PREFIX + IdWorker.getId());
		userInfo.setAvatarUrl(DEFAULT_AVATAR_URL);
		userInfo.setIsVip(0);
		userInfo.setStatus(SystemConstant.USER_STATUS_NORMAL);
		userInfoMapper.insert(userInfo);
		log.info("微信登录自动注册新用户，用户ID：{}，openid：{}", userInfo.getId(), openid);
		return userInfo;
	}

}
