package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.user.service.UserCollectService;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.user.service.UserSubscribeService;
import com.atguigu.tingshu.vo.user.UserCollectVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserSubscribeVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理接口")
@RestController
@RequestMapping("api/user")
@SuppressWarnings({"all"})
public class UserInfoApiController {

	@Autowired
	private UserInfoService userInfoService;

	@Autowired
	private UserSubscribeService userSubscribeService;

	@Autowired
	private UserCollectService userCollectService;


	/**
	 * 根据用户ID查询用户信息（服务内部调用，搜索模块使用）
	 * <p>
	 * 不加登录校验：搜索模块建索引时没有用户登录态，只用来取主播昵称与头像
	 *
	 * @param userId 用户ID
	 * @return 用户信息
	 */
	@Operation(summary = "根据用户ID查询用户信息（服务内部调用）")
	@GetMapping("/userInfo/getUserInfo/{userId}")
	public Result<UserInfoVo> getUserInfo(@PathVariable Long userId) {
		return Result.ok(userInfoService.getUserInfo(userId));
	}


	/**
	 * 判断用户是否为有效VIP会员（服务内部调用，专辑模块使用）
	 * <p>
	 * 不加登录校验：专辑模块渲染声音列表时是带着 userId 来的，不需要登录态；
	 * 未登录用户 userId 为 null，返回 false
	 *
	 * @param userId 用户ID
	 * @return true-有效VIP
	 */
	@Operation(summary = "判断用户是否为有效VIP会员（服务内部调用）")
	@GetMapping("/userInfo/getUserIsVip/{userId}")
	public Result<Boolean> getUserIsVip(@PathVariable Long userId) {
		return Result.ok(userInfoService.getUserIsVip(userId));
	}


	/**
	 * 订阅 / 取消订阅专辑（同一个接口来回切）
	 * <p>
	 * 详情页只有一个订阅按钮，点一下切换状态；返回切换后的状态给前端决定弹哪个文案。
	 * 必须登录：未登录点订阅会拿到 208，前端弹登录框
	 *
	 * @param albumId 专辑ID
	 * @return true-订阅成功；false-取消订阅成功
	 */
	@Operation(summary = "订阅/取消订阅专辑")
	@GuiGuLogin
	@GetMapping("/userInfo/subscribe/{albumId}")
	public Result<Boolean> subscribe(@PathVariable Long albumId) {
		Boolean subscribed = userSubscribeService.subscribeAlbum(AuthContextHolder.getUserId(), albumId);
		return Result.ok(subscribed);
	}


	/**
	 * 查询当前用户是否订阅了某张专辑
	 * <p>
	 * 详情页 onLoad 就会调，未登录时也必须返回 200，
	 * 否则一进详情页就弹「登录过期」；未登录统一返回 false
	 *
	 * @param albumId 专辑ID
	 * @return true-已订阅
	 */
	@Operation(summary = "是否订阅专辑")
	@GuiGuLogin(required = false)
	@GetMapping("/userInfo/isSubscribe/{albumId}")
	public Result<Boolean> isSubscribe(@PathVariable Long albumId) {
		Boolean subscribed = userSubscribeService.isSubscribeAlbum(AuthContextHolder.getUserId(), albumId);
		return Result.ok(subscribed);
	}


	/**
	 * 收藏 / 取消收藏声音（同一个接口来回切）
	 * <p>
	 * 播放页只有一个收藏按钮，点一下切换状态；返回切换后的状态给前端决定弹哪个文案
	 *
	 * @param trackId 声音ID
	 * @return true-收藏成功；false-取消收藏成功
	 */
	@Operation(summary = "收藏/取消收藏声音")
	@GuiGuLogin
	@GetMapping("/userInfo/collect/{trackId}")
	public Result<Boolean> collect(@PathVariable Long trackId) {
		Boolean collected = userCollectService.collectTrack(AuthContextHolder.getUserId(), trackId);
		return Result.ok(collected);
	}


	/**
	 * 查询当前用户是否收藏了某个声音
	 * <p>
	 * 播放页进入时就会调，未登录时也必须返回 200；未登录统一返回 false
	 *
	 * @param trackId 声音ID
	 * @return true-已收藏
	 */
	@Operation(summary = "是否收藏声音")
	@GuiGuLogin(required = false)
	@GetMapping("/userInfo/isCollect/{trackId}")
	public Result<Boolean> isCollect(@PathVariable Long trackId) {
		Boolean collected = userCollectService.isCollectTrack(AuthContextHolder.getUserId(), trackId);
		return Result.ok(collected);
	}


	/**
	 * 分页查询当前用户订阅的专辑（我的-订阅）
	 *
	 * @param page  页码
	 * @param limit 每页条数
	 * @return 订阅专辑分页结果
	 */
	@Operation(summary = "查询用户订阅专辑分页列表")
	@GuiGuLogin
	@GetMapping("/userInfo/findUserSubscribePage/{page}/{limit}")
	public Result<Page<UserSubscribeVo>> findUserSubscribePage(@PathVariable int page, @PathVariable int limit) {
		Page<UserSubscribeVo> pageResult = userSubscribeService.findUserSubscribePage(AuthContextHolder.getUserId(), page, limit);
		return Result.ok(pageResult);
	}


	/**
	 * 分页查询当前用户收藏的声音（我的-收藏）
	 *
	 * @param page  页码
	 * @param limit 每页条数
	 * @return 收藏声音分页结果
	 */
	@Operation(summary = "查询用户收藏声音分页列表")
	@GuiGuLogin
	@GetMapping("/userInfo/findUserCollectPage/{page}/{limit}")
	public Result<Page<UserCollectVo>> findUserCollectPage(@PathVariable int page, @PathVariable int limit) {
		Page<UserCollectVo> pageResult = userCollectService.findUserCollectPage(AuthContextHolder.getUserId(), page, limit);
		return Result.ok(pageResult);
	}

}
