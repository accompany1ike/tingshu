package com.atguigu.tingshu.vo.album;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 声音购买选项（详情页「购买本集」弹窗用）
 * <p>
 * 弹窗里列出两种买法：单集购买、整专辑购买。前端只读 name / price，
 * 点某一项时把 trackId + trackCount 一起带进下单接口，
 * 所以价格必须和下单时服务端算出来的一致，避免两边算价口径不同
 */
@Data
@Schema(description = "声音购买选项")
public class TrackPaidSettingVo {

	@Schema(description = "购买方式名称，如「单集购买」「整专辑购买」")
	private String name;

	@Schema(description = "该购买方式的价格，单位元")
	private BigDecimal price;

	@Schema(description = "声音id")
	private Long trackId;

	@Schema(description = "购买集数：单集为 1，整专辑为专辑总集数")
	private Integer trackCount;

}
