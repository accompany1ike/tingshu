package com.atguigu.tingshu.vo.search;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 专辑详情（页面展示用的聚合对象）
 * <p>
 * 专辑详情页需要四份数据：专辑本体（标题、简介、价格、付费类型…）、三级分类名称、
 * 四项统计数、主播昵称与头像，这里一次性聚合返回，避免前端多次请求
 */
@Data
@Schema(description = "专辑详情")
public class AlbumDetailVo implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "专辑信息（含标签集合）")
	private AlbumInfo albumInfo;

	@Schema(description = "专辑所属的一、二、三级分类")
	private BaseCategoryView baseCategoryView;

	@Schema(description = "专辑统计信息（播放量、订阅量、购买量、评论数）")
	private AlbumStatVo albumStatVo;

	@Schema(description = "主播信息（只保留昵称与头像，其它字段不回传）")
	private UserInfoVo announcer;

}
