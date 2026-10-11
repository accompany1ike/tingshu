package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserListenProcessListVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;

public interface UserListenProcessService {

	/**
	 * 更新播放进度
	 * <p>
	 * 一次上报做两件事：把进度写进 MongoDB（同一条记录覆盖更新），
	 * 再发 MQ 通知专辑模块把声音与专辑的播放量加一
	 *
	 * @param userId              当前登录用户ID
	 * @param userListenProcessVo 播放进度（专辑ID、声音ID、跳出秒数）
	 */
	void updateListenProcess(Long userId, UserListenProcessVo userListenProcessVo);


	/**
	 * 查询某个声音上次播放的跳出秒数（播放页续播用）
	 * <p>
	 * 进度存在 MongoDB，直接取上一次上报的值即可，不需要回源 MySQL
	 *
	 * @param userId  当前登录用户ID
	 * @param trackId 声音ID
	 * @return 跳出秒数；没有播放记录时返回 0
	 */
	BigDecimal getTrackBreakSecond(Long userId, Long trackId);


	/**
	 * 查询用户最近一次播放的声音（"听专辑"入口续播用）
	 * <p>
	 * 底部导航的"听专辑"按钮不带参数跳播放页，播放页拿不到 trackId/albumId，
	 * 就靠这个接口回捞上一次听的是哪一集；配合 getTrackBreakSecond 定位到上次的跳出位置
	 *
	 * @param userId 当前登录用户ID
	 * @return 最近一次播放记录；没有历史时返回 albumId/trackId 为 0 的空对象（不返回 null，
	 * 前端拿到 null 会直接在 data.trackId 上抛错）
	 */
	UserListenProcessListVo getLatelyTrack(Long userId);


	/**
	 * 分页查询当前用户的播放历史（我的-历史）
	 * <p>
	 * 只展示「播放过声音」的记录：浏览专辑留下的记录（trackId=0）和已删除的记录都不返回；
	 * 标题、封面、时长、播放比例由这里回源专辑模块补齐
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码，从 1 开始
	 * @param limit  每页条数
	 * @return 播放历史分页结果
	 */
	Page<UserListenProcessListVo> findUserPage(Long userId, int page, int limit);


	/**
	 * 删除一条播放历史（我的-历史-左滑删除）
	 * <p>
	 * 前端传的是 Mongo 的 _id，物理删除该条记录；重听时 updateListenProcess 会重新写回来。
	 * 带 userId 条件是为了防止越权删别人的记录
	 *
	 * @param userId 当前登录用户ID
	 * @param id     播放记录ID（Mongo _id）
	 */
	void deleteListenProcess(Long userId, String id);

}
