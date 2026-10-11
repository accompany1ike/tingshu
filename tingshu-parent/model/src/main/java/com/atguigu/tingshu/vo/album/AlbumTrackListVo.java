package com.atguigu.tingshu.vo.album;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Schema(description = "用户专辑声音列表信息")
public class AlbumTrackListVo {

	@Schema(description = "声音id")
	private Long trackId;

	@Schema(description = "标题")
	private String trackTitle;

	@Schema(description = "声音媒体时长，单位秒")
	private BigDecimal mediaDuration;

	@Schema(description = "排序")
	private Integer orderNum;

	@Schema(description = "播放量")
	private Integer playStatNum;

	/**
	 * 页面（专辑详情页声音列表）实际读取的就是这个字段
	 */
	@Schema(description = "评论数")
	private Integer commentStatNum;

	/**
	 * 历史遗留字段名，全项目没有任何调用方使用；保留只为避免影响未知的旧引用，
	 * 服务端会与 commentStatNum 填相同的值，新代码请直接用 commentStatNum
	 */
	@Schema(description = "评论数（历史遗留字段，等同 commentStatNum）")
	private Integer albumCommentStatNum;

	@Schema(description = "发布时间")
	private Date createTime;

	@Schema(description = "是否显示付费标识，isShowPiadMark=true，则显示付费标识")
	private Boolean isShowPaidMark = false;

}
