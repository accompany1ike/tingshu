package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "微信授权登录接口")
@RestController
@RequestMapping("/api/user/wxLogin")
@Slf4j
public class WxLoginApiController {

    @Autowired
    private UserInfoService userInfoService;


    /**
     * 微信小程序登录
     * <p>
     * 小程序端 wx.login 拿到 code 后调用本接口；用户首次登录会自动注册。
     * 返回的 data.token 需要放在后续所有请求的 token 请求头里
     *
     * @param code 微信登录凭证
     * @return token-访问令牌，userId-用户ID
     */
    @Operation(summary = "微信小程序登录")
    @GetMapping("/wxLogin/{code}")
    public Result<Map<String, Object>> wxLogin(@PathVariable String code) {
        return Result.ok(userInfoService.wxLogin(code));
    }


    /**
     * 获取当前登录用户信息
     *
     * @return 用户信息
     */
    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/getUserInfo")
    @GuiGuLogin
    public Result<UserInfoVo> getUserInfo() {
        return Result.ok(userInfoService.getUserInfo(AuthContextHolder.getUserId()));
    }


    /**
     * 更新当前登录用户信息（昵称、头像）
     *
     * @param userInfoVo 待更新的用户信息
     * @return 操作结果
     */
    @Operation(summary = "更新当前登录用户信息")
    @PostMapping("/updateUser")
    @GuiGuLogin
    public Result updateUser(@RequestBody UserInfoVo userInfoVo) {
        userInfoService.updateUserInfo(AuthContextHolder.getUserId(), userInfoVo);
        return Result.ok();
    }

}
