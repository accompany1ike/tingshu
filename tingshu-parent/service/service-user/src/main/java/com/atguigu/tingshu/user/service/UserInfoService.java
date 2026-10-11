package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

public interface UserInfoService extends IService<UserInfo> {


    /**
     * 微信小程序登录
     * <p>
     * 用 wx.login 拿到的 code 换取 openid，用户不存在则自动注册；
     * 登录成功后把用户信息写入 Redis 登录态，并返回访问令牌
     *
     * @param code 微信登录凭证（只能用一次）
     * @return token-访问令牌，userId-用户ID
     */
    Map<String, Object> wxLogin(String code);


    /**
     * 获取当前登录用户信息
     *
     * @param userId 当前登录用户ID
     * @return 用户信息
     */
    UserInfoVo getUserInfo(Long userId);


    /**
     * 更新当前登录用户信息（昵称、头像）
     *
     * @param userId     当前登录用户ID
     * @param userInfoVo 待更新的用户信息
     */
    void updateUserInfo(Long userId, UserInfoVo userInfoVo);


    /**
     * 判断用户是否为有效VIP会员（服务内部调用）
     * <p>
     * 专辑模块渲染声音列表时用它决定 VIP 专辑要不要显示付费标识
     *
     * @param userId 用户ID，未登录时传 null
     * @return true-有效VIP；用户不存在、非VIP、或会员已过期都返回 false
     */
    Boolean getUserIsVip(Long userId);

}
