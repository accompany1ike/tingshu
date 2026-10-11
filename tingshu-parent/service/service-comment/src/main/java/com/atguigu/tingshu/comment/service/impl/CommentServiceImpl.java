package com.atguigu.tingshu.comment.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.comment.mq.CommentStatMqSender;
import com.atguigu.tingshu.comment.service.CommentService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.comment.Comment;
import com.atguigu.tingshu.model.comment.CommentPraise;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.comment.CommentVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 专辑评论：发表、回复、点赞、删除
 * <p>
 * 评论按专辑维度聚合：接口路径里的 trackId 是前端为将来「按声音看评论」预留的，
 * 当前 Comment 实体没有 trackId 字段，所以查询只按 albumId 过滤，trackId 不参与筛选。
 * <p>
 * 数据全部落在 MongoDB，按 albumId 取模分表（comment_{albumId%100}、commentPraise_{albumId%100}），
 * 集合名统一走 MongoUtil，不在业务里手写。
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class CommentServiceImpl implements CommentService {

	/** 一级评论的 replyCommentId 约定值，前端一级评论固定传 "0" */
	private static final String TOP_COMMENT_REPLY_ID = "0";

	/** 默认每页条数（前端 z-paging 默认 30，这里兜个底） */
	private static final int DEFAULT_PAGE_SIZE = 10;

	/** 单页最大条数：防止前端传个超大分页把 Mongo 拖垮 */
	private static final int MAX_PAGE_SIZE = 100;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private AlbumFeignClient albumFeignClient;

	@Autowired
	private UserFeignClient userFeignClient;

	@Autowired
	private CommentStatMqSender commentStatMqSender;


	/**
	 * 分页查询专辑评论
	 * <p>
	 * 只对一级评论分页，回复跟着一级评论一起返回，避免「一级评论翻页了、回复却被切到下一页」的错位
	 *
	 * @param albumId 专辑ID
	 * @param trackId 声音ID（当前不参与过滤）
	 * @param page    页码
	 * @param limit   每页条数
	 * @param userId  当前登录用户ID，未登录传 null
	 * @return 评论分页结果
	 */
	@Override
	public Page<Comment> findCommentPage(Long albumId, Long trackId, Integer page, Integer limit, Long userId) {
		//1.校验与分页参数兜底
		if (albumId == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑ID不能为空");
		}
		int pageNo = (page == null || page < 1) ? 1 : page;
		int pageSize = (limit == null || limit < 1) ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
		String commentCollection = this.commentCollection(albumId);

		//2.一级评论分页：总数单独查一次，find 里带 skip/limit 时拿不到总数
		Query topQuery = new Query(Criteria.where("albumId").is(albumId)
				.and("replyCommentId").is(TOP_COMMENT_REPLY_ID));
		long total = mongoTemplate.count(topQuery, Comment.class, commentCollection);
		Page<Comment> pageResult = new Page<>(pageNo, pageSize, total);
		if (total == 0) {
			pageResult.setRecords(new ArrayList<>());
			return pageResult;
		}
		topQuery.with(Sort.by(Sort.Direction.DESC, "createTime"))
				.skip((long) (pageNo - 1) * pageSize)
				.limit(pageSize);
		List<Comment> topComments = mongoTemplate.find(topQuery, Comment.class, commentCollection);

		//3.回复拍平：一级回复 + 二级回复都挂到对应的一级评论下
		Map<String, List<Comment>> replyMap = this.buildReplyMap(topComments, commentCollection);

		//4.点赞状态与删除标记：把这一页涉及到的评论一次性查完，避免每条评论查一次
		List<Comment> allComments = new ArrayList<>(topComments);
		replyMap.values().forEach(allComments::addAll);
		this.fillPraiseAndDeleteMark(allComments, albumId, userId);

		//5.组装返回
		for (Comment comment : topComments) {
			comment.setReplyCommentList(replyMap.getOrDefault(comment.getId(), new ArrayList<>()));
		}
		pageResult.setRecords(topComments);
		return pageResult;
	}


	/**
	 * 发表评论或回复
	 *
	 * @param userId    当前登录用户ID
	 * @param commentVo 评论内容
	 */
	@Override
	public void saveComment(Long userId, CommentVo commentVo) {
		//1.校验：登录态、专辑、内容
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (commentVo == null || commentVo.getAlbumId() == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑ID不能为空");
		}
		if (!StringUtils.hasText(commentVo.getContent())) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "评论内容不能为空");
		}
		AlbumInfo albumInfo = this.getData(albumFeignClient.getAlbumInfoById(commentVo.getAlbumId()));
		if (albumInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "专辑不存在");
		}

		//2.用户昵称头像存快照：评论列表是高频读、用户信息是低频变，存下来省掉每次查用户服务
		UserInfoVo userInfo = this.getData(userFeignClient.getUserInfo(userId));

		Comment comment = new Comment();
		comment.setAlbumId(commentVo.getAlbumId());
		comment.setUserId(userId);
		comment.setNickname(userInfo == null || !StringUtils.hasText(userInfo.getNickname())
				? "听书用户" : userInfo.getNickname());
		comment.setAvatarUrl(userInfo == null ? null : userInfo.getAvatarUrl());
		comment.setContent(commentVo.getContent().trim());
		//replyCommentId 为空按一级评论处理
		comment.setReplyCommentId(StringUtils.hasText(commentVo.getReplyCommentId())
				? commentVo.getReplyCommentId() : TOP_COMMENT_REPLY_ID);
		comment.setPraiseCount(0);
		comment.setAlbumCommentScore(commentVo.getAlbumCommentScore());
		comment.setCreateTime(new Date());
		mongoTemplate.insert(comment, this.commentCollection(commentVo.getAlbumId()));
		log.info("评论发表成功，专辑ID：{}，评论ID：{}，用户ID：{}",
				commentVo.getAlbumId(), comment.getId(), userId);

		//3.通知专辑模块评论数 +1（专辑模块落库后会顺带刷新索引里的统计与热度）
		commentStatMqSender.sendCommentStat(commentVo.getAlbumId(), 1);
	}


	/**
	 * 删除评论
	 *
	 * @param userId    当前登录用户ID
	 * @param albumId   专辑ID
	 * @param commentId 评论ID
	 */
	@Override
	public void removeComment(Long userId, Long albumId, String commentId) {
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (albumId == null || !StringUtils.hasText(commentId)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "评论ID不能为空");
		}
		String commentCollection = this.commentCollection(albumId);
		Comment comment = mongoTemplate.findById(commentId, Comment.class, commentCollection);
		//已经删过了就当成功，前端重复点删除不该报错
		if (comment == null) {
			log.info("评论不存在，跳过删除，专辑ID：{}，评论ID：{}", albumId, commentId);
			return;
		}
		//只能删自己发的
		if (!userId.equals(comment.getUserId())) {
			throw new GuiguException(ResultCodeEnum.PERMISSION.getCode(), "只能删除自己发表的评论");
		}

		//1.删一级评论时把它下面的回复一起删掉，否则回复会变成孤儿数据
		Query replyQuery = new Query(Criteria.where("replyCommentId").is(commentId));
		long replyCount = mongoTemplate.count(replyQuery, Comment.class, commentCollection);
		mongoTemplate.remove(replyQuery, Comment.class, commentCollection);
		mongoTemplate.remove(new Query(Criteria.where("_id").is(commentId)), Comment.class, commentCollection);

		//2.点赞记录一起清掉，避免残留脏数据把评论数算错
		mongoTemplate.remove(new Query(Criteria.where("commentId").is(commentId)),
				CommentPraise.class, this.praiseCollection(albumId));

		log.info("评论删除成功，专辑ID：{}，评论ID：{}，连带删除回复：{} 条", albumId, commentId, replyCount);

		//3.评论数按实际删除条数扣减（负数增量，专辑模块那边是原子自增，能直接减）
		commentStatMqSender.sendCommentStat(albumId, -(int) (1 + replyCount));
	}


	/**
	 * 点赞 / 取消点赞评论
	 *
	 * @param userId    当前登录用户ID
	 * @param albumId   专辑ID
	 * @param commentId 评论ID
	 */
	@Override
	public void praiseComment(Long userId, Long albumId, String commentId) {
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (albumId == null || !StringUtils.hasText(commentId)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "评论ID不能为空");
		}
		String commentCollection = this.commentCollection(albumId);
		String praiseCollection = this.praiseCollection(albumId);
		if (!mongoTemplate.exists(new Query(Criteria.where("_id").is(commentId)), Comment.class, commentCollection)) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "评论不存在");
		}

		//1.判断当前用户有没有点过赞，点过就取消、没点过就新增
		Query praiseQuery = new Query(Criteria.where("userId").is(userId).and("commentId").is(commentId));
		int delta;
		if (mongoTemplate.exists(praiseQuery, CommentPraise.class, praiseCollection)) {
			mongoTemplate.remove(praiseQuery, CommentPraise.class, praiseCollection);
			delta = -1;
		} else {
			CommentPraise commentPraise = new CommentPraise();
			commentPraise.setAlbumId(albumId);
			commentPraise.setCommentId(commentId);
			commentPraise.setUserId(userId);
			commentPraise.setCreateTime(new Date());
			mongoTemplate.insert(commentPraise, praiseCollection);
			delta = 1;
		}

		//2.点赞数用 $inc 原子自增：先查再回写会在并发点赞时丢计数
		mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(commentId)),
				new Update().inc("praiseCount", delta), Comment.class, commentCollection);
		log.info("评论点赞状态已切换，专辑ID：{}，评论ID：{}，用户ID：{}，点赞数增量：{}",
				albumId, commentId, userId, delta);
	}


	/**
	 * 组装一级评论下的回复
	 * <p>
	 * 分两轮查：第一轮查直接回复一级评论的，第二轮查回复「回复」的。
	 * 第二轮查出来的评论挂到「它回复的那条回复所属的一级评论」下，并把自己的 parent 指向被回复人，
	 * 前端就是靠 parent 显示成「回复 xxx」。再深的嵌套前端也不渲染，所以到此为止。
	 *
	 * @param topComments     本页的一级评论
	 * @param commentCollection 评论集合名
	 * @return 一级评论ID -> 它下面的回复（按时间正序）
	 */
	private Map<String, List<Comment>> buildReplyMap(List<Comment> topComments, String commentCollection) {
		Map<String, List<Comment>> replyMap = new HashMap<>();
		List<String> topIds = new ArrayList<>();
		topComments.forEach(comment -> topIds.add(comment.getId()));

		//1.一级回复
		List<Comment> level1 = mongoTemplate.find(
				new Query(Criteria.where("replyCommentId").in(topIds))
						.with(Sort.by(Sort.Direction.ASC, "createTime")),
				Comment.class, commentCollection);
		if (CollectionUtils.isEmpty(level1)) {
			return replyMap;
		}
		Map<String, Comment> level1Map = new HashMap<>();
		for (Comment reply : level1) {
			replyMap.computeIfAbsent(reply.getReplyCommentId(), key -> new ArrayList<>()).add(reply);
			level1Map.put(reply.getId(), reply);
		}

		//2.二级回复
		List<Comment> level2 = mongoTemplate.find(
				new Query(Criteria.where("replyCommentId").in(new ArrayList<>(level1Map.keySet())))
						.with(Sort.by(Sort.Direction.ASC, "createTime")),
				Comment.class, commentCollection);
		for (Comment reply : level2) {
			Comment parent = level1Map.get(reply.getReplyCommentId());
			if (parent == null) {
				continue;
			}
			reply.setParent(parent);
			replyMap.computeIfAbsent(parent.getReplyCommentId(), key -> new ArrayList<>()).add(reply);
		}
		return replyMap;
	}


	/**
	 * 填充「当前用户是否点赞」与「是否可删除」
	 * <p>
	 * 一次把本页所有评论的点赞记录查出来再比对，避免每条评论查一次 Mongo
	 *
	 * @param allComments 本页涉及到的所有评论（一级 + 回复）
	 * @param albumId     专辑ID
	 * @param userId      当前登录用户ID，未登录传 null
	 */
	private void fillPraiseAndDeleteMark(List<Comment> allComments, Long albumId, Long userId) {
		Set<String> praisedCommentIds = new HashSet<>();
		if (userId != null && !CollectionUtils.isEmpty(allComments)) {
			List<String> commentIds = new ArrayList<>();
			allComments.forEach(comment -> commentIds.add(comment.getId()));
			Query praiseQuery = new Query(Criteria.where("userId").is(userId).and("commentId").in(commentIds));
			List<CommentPraise> praiseList = mongoTemplate.find(praiseQuery, CommentPraise.class,
					this.praiseCollection(albumId));
			praiseList.forEach(praise -> praisedCommentIds.add(praise.getCommentId()));
		}
		for (Comment comment : allComments) {
			comment.setIsPraise(praisedCommentIds.contains(comment.getId()));
			//前端只有 deleteMark == "1" 时才显示删除按钮，所以这里用字符串而不是布尔
			comment.setDeleteMark(userId != null && userId.equals(comment.getUserId()) ? "1" : "0");
		}
	}


	/**
	 * @param albumId 专辑ID
	 * @return 评论集合名：comment_{albumId%100}
	 */
	private String commentCollection(Long albumId) {
		return MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.COMMENT, albumId);
	}


	/**
	 * @param albumId 专辑ID
	 * @return 评论点赞集合名：commentPraise_{albumId%100}
	 */
	private String praiseCollection(Long albumId) {
		return MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.COMMENT_PRAISE, albumId);
	}


	/**
	 * 远程调用结果统一取值：服务降级、业务异常时 data 都是 null，调用方按「没查到」处理
	 *
	 * @param result 远程调用结果
	 * @return 业务数据
	 */
	private <T> T getData(Result<T> result) {
		return result == null ? null : result.getData();
	}

}
