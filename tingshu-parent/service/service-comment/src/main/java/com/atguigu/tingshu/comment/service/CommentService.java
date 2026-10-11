package com.atguigu.tingshu.comment.service;

import com.atguigu.tingshu.model.comment.Comment;
import com.atguigu.tingshu.vo.comment.CommentVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface CommentService {

	/**
	 * 分页查询专辑评论
	 * <p>
	 * 只分页一级评论，每条一级评论带上它下面的回复（拍平成一层，二级回复通过 parent 标记被回复人）；
	 * 同时按当前登录用户补上「是否已点赞」和「是否可删除」
	 *
	 * @param albumId 专辑ID
	 * @param trackId 声音ID（前端传了但评论按专辑聚合，这里不参与过滤）
	 * @param page    页码，从 1 开始
	 * @param limit   每页条数
	 * @param userId  当前登录用户ID，未登录传 null
	 * @return 评论分页结果
	 */
	Page<Comment> findCommentPage(Long albumId, Long trackId, Integer page, Integer limit, Long userId);


	/**
	 * 发表评论或回复
	 *
	 * @param userId    当前登录用户ID
	 * @param commentVo 评论内容（专辑ID、内容、评分、被回复的评论ID）
	 */
	void saveComment(Long userId, CommentVo commentVo);


	/**
	 * 删除评论
	 * <p>
	 * 只能删自己发的；删一级评论时它下面的回复一并删掉，评论数按实际删除条数扣减
	 *
	 * @param userId    当前登录用户ID
	 * @param albumId   专辑ID
	 * @param commentId 评论ID
	 */
	void removeComment(Long userId, Long albumId, String commentId);


	/**
	 * 点赞 / 取消点赞评论
	 * <p>
	 * 同一个接口来回切：没有点赞记录就新增并 +1，已有记录就删掉并 -1
	 *
	 * @param userId    当前登录用户ID
	 * @param albumId   专辑ID
	 * @param commentId 评论ID
	 */
	void praiseComment(Long userId, Long albumId, String commentId);

}
