package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserSubscribeVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface UserSubscribeService {

	/**
	 * 订阅 / 取消订阅专辑（同一个接口来回切）
	 * <p>
	 * 前端详情页只有一个订阅按钮，点一下切换状态并弹「订阅成功 / 取消订阅成功」，
	 * 所以这里返回切换后的状态：true-已订阅，false-已取消
	 *
	 * @param userId  当前登录用户ID
	 * @param albumId 专辑ID
	 * @return true-订阅成功；false-取消订阅成功
	 */
	Boolean subscribeAlbum(Long userId, Long albumId);


	/**
	 * 查询当前用户是否订阅了某张专辑
	 *
	 * @param userId  当前登录用户ID，未登录传 null
	 * @param albumId 专辑ID
	 * @return true-已订阅
	 */
	Boolean isSubscribeAlbum(Long userId, Long albumId);


	/**
	 * 分页查询当前用户订阅的专辑
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码，从 1 开始
	 * @param limit  每页条数
	 * @return 订阅专辑分页结果（含专辑标题、封面、是否完结）
	 */
	Page<UserSubscribeVo> findUserSubscribePage(Long userId, int page, int limit);

}
