package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.user.UserListenProcessListVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@Tag(name = "用户声音播放进度管理接口")
@RestController
@RequestMapping("api/user")
@SuppressWarnings({"all"})
public class UserListenProcessApiController {

	@Autowired
	private UserListenProcessService userListenProcessService;


	/**
	 * 更新播放进度
	 * <p>
	 * 播放页每 10 秒自动上报一次，把当前跳出秒数写进 MongoDB，
	 * 同时发 MQ 让专辑模块把声音与专辑的播放量各加一
	 *
	 * @param userListenProcessVo 播放进度（专辑ID、声音ID、跳出秒数）
	 * @return 操作结果
	 */
	@Operation(summary = "更新播放进度")
	@GuiGuLogin
	@PostMapping("/userListenProcess/updateListenProcess")
	public Result updateListenProcess(@RequestBody UserListenProcessVo userListenProcessVo) {
		userListenProcessService.updateListenProcess(AuthContextHolder.getUserId(), userListenProcessVo);
		return Result.ok();
	}


	/**
	 * 获取声音上次播放的跳出秒数
	 * <p>
	 * 播放页进入时调用，拿到上次听到哪儿，直接 seek 过去接着播；没有记录返回 0
	 *
	 * @param trackId 声音ID
	 * @return 跳出秒数
	 */
	@Operation(summary = "获取声音上次播放的跳出秒数")
	@GuiGuLogin
	@GetMapping("/userListenProcess/getTrackBreakSecond/{trackId}")
	public Result<BigDecimal> getTrackBreakSecond(@PathVariable Long trackId) {
		BigDecimal breakSecond = userListenProcessService.getTrackBreakSecond(AuthContextHolder.getUserId(), trackId);
		return Result.ok(breakSecond);
	}


	/**
	 * 获取最近一次播放的声音
	 * <p>
	 * 底部导航"听专辑"按钮不带参数跳播放页，播放页靠这个接口回捞上次听的是哪一集，
	 * 再调 getTrackBreakSecond 拿到跳出位置，实现"接着上次继续听"
	 *
	 * @return 最近一次播放记录；没有历史时 albumId/trackId 为 0
	 */
	@Operation(summary = "获取最近一次播放的声音")
	@GuiGuLogin
	@GetMapping("/userListenProcess/getLatelyTrack")
	public Result<UserListenProcessListVo> getLatelyTrack() {
		UserListenProcessListVo latelyTrack = userListenProcessService.getLatelyTrack(AuthContextHolder.getUserId());
		return Result.ok(latelyTrack);
	}


	/**
	 * 分页查询当前用户的播放历史（我的-历史）
	 * <p>
	 * 只返回「播放过声音」的记录，标题封面时长由用户模块回源专辑模块补齐
	 *
	 * @param page  页码
	 * @param limit 每页条数
	 * @return 播放历史分页结果
	 */
	@Operation(summary = "查询用户播放历史分页列表")
	@GuiGuLogin
	@GetMapping("/userListenProcess/findUserPage/{page}/{limit}")
	public Result<Page<UserListenProcessListVo>> findUserPage(@PathVariable int page, @PathVariable int limit) {
		Page<UserListenProcessListVo> pageResult = userListenProcessService.findUserPage(AuthContextHolder.getUserId(), page, limit);
		return Result.ok(pageResult);
	}


	/**
	 * 删除一条播放历史（我的-历史-左滑删除）
	 *
	 * @param id 播放记录ID（Mongo _id）
	 * @return 操作结果
	 */
	@Operation(summary = "删除播放历史")
	@GuiGuLogin
	@DeleteMapping("/userListenProcess/delete/{id}")
	public Result deleteHistory(@PathVariable String id) {
		userListenProcessService.deleteListenProcess(AuthContextHolder.getUserId(), id);
		return Result.ok();
	}

}
