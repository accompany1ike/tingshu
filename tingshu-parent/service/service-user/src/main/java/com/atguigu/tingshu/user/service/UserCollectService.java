package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserCollectVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface UserCollectService {

	/**
	 * 收藏 / 取消收藏声音（同一个接口来回切）
	 * <p>
	 * 播放页只有一个收藏按钮，点一下切换状态并弹「收藏成功 / 取消收藏成功」，
	 * 所以这里返回切换后的状态：true-已收藏，false-已取消
	 *
	 * @param userId  当前登录用户ID
	 * @param trackId 声音ID
	 * @return true-收藏成功；false-取消收藏成功
	 */
	Boolean collectTrack(Long userId, Long trackId);


	/**
	 * 查询当前用户是否收藏了某个声音
	 *
	 * @param userId  当前登录用户ID，未登录传 null
	 * @param trackId 声音ID
	 * @return true-已收藏
	 */
	Boolean isCollectTrack(Long userId, Long trackId);


	/**
	 * 分页查询当前用户收藏的声音
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码，从 1 开始
	 * @param limit  每页条数
	 * @return 收藏声音分页结果（含声音标题、封面、时长、所属专辑标题）
	 */
	Page<UserCollectVo> findUserCollectPage(Long userId, int page, int limit);

}
