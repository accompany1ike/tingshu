package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.user.service.UserPaidTrackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "用户已购声音接口")
@RestController
@RequestMapping("api/user")
@SuppressWarnings({"all"})
public class UserPaidTrackApiController {

	@Autowired
	private UserPaidTrackService userPaidTrackService;


	/**
	 * 查询用户已购买的声音ID集合（服务内部调用，专辑模块使用）
	 * <p>
	 * 不加登录校验：专辑模块渲染声音列表时带着 userId 与当前页的声音ID来查，
	 * 只需要判断"这批声音里哪些已经买过"，不涉及登录态
	 *
	 * @param userId      用户ID
	 * @param trackIdList 待判断的声音ID集合
	 * @return 已购买的声音ID集合
	 */
	@Operation(summary = "查询用户已购买的声音ID集合（服务内部调用）")
	@PostMapping("/userPaidTrack/getUserPaidTrackIdList/{userId}")
	public Result<List<Long>> getUserPaidTrackIdList(@PathVariable Long userId,
													 @RequestBody List<Long> trackIdList) {
		return Result.ok(userPaidTrackService.getUserPaidTrackIdList(userId, trackIdList));
	}

}
