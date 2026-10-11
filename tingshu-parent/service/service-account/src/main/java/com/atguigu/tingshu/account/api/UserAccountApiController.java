package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "用户账户管理")
@RestController
@RequestMapping("api/account")
@SuppressWarnings({"all"})
public class UserAccountApiController {

	@Autowired
	private UserAccountService userAccountService;

	/**
	 * 获取当前登录用户可用余额
	 * <p>
	 * 前端 stores/user.js 是把返回的 data 直接当成数字赋给 amount 的，所以这里返回的就是金额本身，
	 * 不能包成对象
	 *
	 * @return 可用余额
	 */
	@Operation(summary = "获取账户可用余额")
	@GuiGuLogin
	@GetMapping("/userAccount/getAvailableAmount")
	public Result<BigDecimal> getAvailableAmount() {
		return Result.ok(userAccountService.getAvailableAmount(AuthContextHolder.getUserId()));
	}

	/**
	 * 初始化用户账户（服务内部调用）
	 * <p>
	 * 用户注册成功后由 user 模块调用，账户余额从 0 开始；接口幂等，重复调用不会重复开户
	 *
	 * @param userId 用户ID
	 * @return 统一返回结果
	 */
	@Operation(summary = "初始化用户账户")
	@PostMapping("/userAccount/initUserAccount/{userId}")
	public Result initUserAccount(@PathVariable Long userId) {
		userAccountService.initUserAccount(userId);
		return Result.ok();
	}

}
