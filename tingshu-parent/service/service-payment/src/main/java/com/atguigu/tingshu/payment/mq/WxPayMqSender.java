package com.atguigu.tingshu.payment.mq;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 支付模块的消息发送器
 * <p>
 * 支付成功只需要把订单号播出去，订单模块自己去改状态、发货、更新购买量；
 * 支付模块不碰订单库，也不关心发货细节
 */
@Slf4j
@Component
public class WxPayMqSender {

	@Autowired
	private RabbitService rabbitService;


	/**
	 * 发送订单支付成功消息
	 *
	 * @param orderNo 订单号
	 */
	public void sendOrderPaySuccess(String orderNo) {
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_ORDER, MqConst.ROUTING_ORDER_PAY_SUCCESS, orderNo);
			log.info("订单支付成功消息发送成功，订单号：{}", orderNo);
		} catch (Exception e) {
			log.error("订单支付成功消息发送失败，订单号：{}", orderNo, e);
		}
	}


	/**
	 * 发送充值支付成功消息
	 * <p>
	 * 和订单支付成功共用 EXCHANGE_ORDER，靠路由键区分：账户模块消费后给余额入账
	 *
	 * @param orderNo 充值订单号
	 */
	public void sendRechargePaySuccess(String orderNo) {
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_ORDER, MqConst.ROUTING_RECHARGE_PAY_SUCCESS, orderNo);
			log.info("充值支付成功消息发送成功，充值订单号：{}", orderNo);
		} catch (Exception e) {
			log.error("充值支付成功消息发送失败，充值订单号：{}", orderNo, e);
		}
	}

}
