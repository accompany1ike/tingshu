package com.atguigu.tingshu.order.listener;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.order.service.OrderInfoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 订单消息监听：支付成功、超时取消
 * <p>
 * 异常必须在这里吃掉：MQ 是自动确认模式，方法抛异常会让消息重新入队、无限重投把日志刷爆。
 * 而且支付成功消息重投时会重复触发发货逻辑，虽然 updateOrderPaid 已经幂等，
 * 但没必要为此制造无谓的重试
 */
@Slf4j
@Component
@SuppressWarnings({"all"})
public class OrderMqListener {

	@Autowired
	private OrderInfoService orderInfoService;

	@Autowired
	private MessageConverter messageConverter;


	/**
	 * 支付成功：改订单状态、通知用户模块发货、专辑购买量加一
	 *
	 * @param message 消息体为订单号
	 */
	@RabbitListener(queues = MqConst.QUEUE_ORDER_PAY_SUCCESS)
	public void onOrderPaySuccess(Message message) {
		String orderNo = this.parseOrderNo(message);
		if (orderNo == null) {
			return;
		}
		log.info("收到订单支付成功消息，订单号：{}", orderNo);
		try {
			orderInfoService.updateOrderPaid(orderNo);
		} catch (Exception e) {
			log.error("订单支付成功处理失败，订单号：{}", orderNo, e);
		}
	}


	/**
	 * 超时取消订单：延迟队列过期后由死信路由过来
	 *
	 * @param message 消息体为订单号
	 */
	@RabbitListener(queues = MqConst.QUEUE_CANCEL_ORDER)
	public void onCancelOrder(Message message) {
		String orderNo = this.parseOrderNo(message);
		if (orderNo == null) {
			return;
		}
		log.info("收到超时取消订单消息，订单号：{}", orderNo);
		try {
			orderInfoService.cancelOrder(orderNo);
		} catch (Exception e) {
			log.error("超时取消订单失败，订单号：{}", orderNo, e);
		}
	}


	/**
	 * 把消息体还原成订单号
	 * <p>
	 * 支付模块发的是纯字符串，消息转换器能直接还原；
	 * 控制台手工投递时可能带上引号或走字节数组分支，这里统一兜底
	 *
	 * @param message 原始消息
	 * @return 订单号，解析失败返回 null
	 */
	private String parseOrderNo(Message message) {
		try {
			Object payload = messageConverter.fromMessage(message);
			String orderNo = payload == null ? null : payload.toString().trim();
			if (!StringUtils.hasText(orderNo)) {
				orderNo = new String(message.getBody(), StandardCharsets.UTF_8).trim();
			}
			//手工投递 JSON 字符串时会带上首尾引号
			orderNo = orderNo.replace("\"", "");
			if (!StringUtils.hasText(orderNo)) {
				log.error("订单消息体为空，消息内容：{}", new String(message.getBody(), StandardCharsets.UTF_8));
				return null;
			}
			return orderNo;
		} catch (Exception e) {
			log.error("订单消息体解析失败，消息内容：{}", new String(message.getBody(), StandardCharsets.UTF_8), e);
			return null;
		}
	}

}
