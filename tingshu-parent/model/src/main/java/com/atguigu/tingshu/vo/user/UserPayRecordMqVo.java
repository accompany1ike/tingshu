package com.atguigu.tingshu.vo.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单支付成功消息（订单模块 -> 用户模块）
 * <p>
 * 用户模块据此「发货」：写已购声音/已购专辑记录、开通会员。
 * <p>
 * 必须实现 Serializable：项目里的消息转换器是 SimpleMessageConverter（Java 序列化），
 * 发非可序列化对象会在发送端直接抛 MessageConversionException
 */
@Data
@Schema(description = "订单支付成功消息")
public class UserPayRecordMqVo implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "用户ID")
	private Long userId;

	@Schema(description = "付款项目类型: 1001-专辑 1002-声音 1003-vip会员")
	private String itemType;

	@Schema(description = "付款项目ID：1001-专辑ID 1002-声音ID 1003-VIP服务配置ID")
	private Long itemId;

}
