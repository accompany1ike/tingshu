package com.atguigu.tingshu.comment.api;

import com.atguigu.tingshu.comment.service.CommentService;
import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.comment.Comment;
import com.atguigu.tingshu.vo.comment.CommentVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "专辑评论管理接口")
@RestController
@RequestMapping("api/comment")
@SuppressWarnings({"all"})
public class CommentApiController {

	@Autowired
	private CommentService commentService;


	/**
	 * 分页查询专辑评论
	 * <p>
	 * 未登录也能看评论（@GuiGuLogin(required = false)），只是拿不到「是否已点赞」和删除按钮
	 *
	 * @param albumId 专辑ID
	 * @param trackId 声音ID（前端路径里带着，当前按专辑聚合评论，不参与过滤）
	 * @param page    页码
	 * @param limit   每页条数
	 * @return 评论分页结果，records 里每条带 replyCommentList / isPraise / deleteMark
	 */
	@Operation(summary = "分页查询专辑评论")
	@GuiGuLogin(required = false)
	@GetMapping("/findCommentPage/{albumId}/{trackId}/{page}/{limit}")
	public Result<Page<Comment>> findCommentPage(@PathVariable Long albumId,
												@PathVariable Long trackId,
												@PathVariable Integer page,
												@PathVariable Integer limit) {
		Page<Comment> pageResult = commentService.findCommentPage(albumId, trackId, page, limit,
				AuthContextHolder.getUserId());
		return Result.ok(pageResult);
	}


	/**
	 * 发表评论或回复
	 * <p>
	 * 一级评论的 replyCommentId 传 "0"，回复传被回复的评论ID
	 *
	 * @param commentVo 评论内容（专辑ID、内容、评分、被回复的评论ID）
	 * @return 操作结果
	 */
	@Operation(summary = "发表评论")
	@GuiGuLogin
	@PostMapping("/save")
	public Result saveComment(@Valid @RequestBody CommentVo commentVo) {
		commentService.saveComment(AuthContextHolder.getUserId(), commentVo);
		return Result.ok();
	}


	/**
	 * 点赞 / 取消点赞评论
	 * <p>
	 * 同一个接口来回切，前端不解析返回值，自己把 isPraise 取反
	 *
	 * @param albumId   专辑ID
	 * @param commentId 评论ID
	 * @return 操作结果
	 */
	@Operation(summary = "点赞或取消点赞评论")
	@GuiGuLogin
	@GetMapping("/praise/{albumId}/{commentId}")
	public Result praiseComment(@PathVariable Long albumId, @PathVariable String commentId) {
		commentService.praiseComment(AuthContextHolder.getUserId(), albumId, commentId);
		return Result.ok();
	}


	/**
	 * 删除评论
	 * <p>
	 * 只能删自己发的；删一级评论时它下面的回复一并删除
	 *
	 * @param albumId   专辑ID
	 * @param commentId 评论ID
	 * @return 操作结果
	 */
	@Operation(summary = "删除评论")
	@GuiGuLogin
	@GetMapping("/remove/{albumId}/{commentId}")
	public Result removeComment(@PathVariable Long albumId, @PathVariable String commentId) {
		commentService.removeComment(AuthContextHolder.getUserId(), albumId, commentId);
		return Result.ok();
	}

}
