package com.atguigu.tingshu.vo.album;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "声音统计信息")
public class TrackStatVo {


	@Schema(description = "播放量")
	private Integer playStatNum;

	@Schema(description = "收藏量")
	private Integer collectStatNum;

	@Schema(description = "点赞量")
	private Integer praiseStatNum;

	@Schema(description = "评论数")
	private Integer commentStatNum;

	@Schema(description = "评论数（旧字段名，已废弃，与 commentStatNum 填同值，待确认后删除）")
	private Integer albumCommentStatNum;

}