package com.atguigu.tingshu.account.listener;

import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 充值支付成功消息监听：给账户加钱
 * <p>
 * 异常必须在这里吃掉：MQ 是自动确认模式，方法抛异常会让消息重新入队、无限重投把日志刷爆；
 * 而且到账本身按充值订单号幂等，重投也不会重复加钱
 */
@Slf4j
@Component
@SuppressWarnings({"all"})
public class RechargePayListener {

	@Autowired
	private RechargeInfoService rechargeInfoService;

	@Autowired
	private MessageConverter messageConverter;


	/**
	 * 充值支付成功：把充值记录置为已支付，并给账户余额入账
	 *
	 * @param message 消息体为充值订单号（字符串）
	 */
	@RabbitListener(queues = MqConst.QUEUE_RECHARGE_PAY_SUCCESS)
	public void onRechargePaySuccess(Message message) {
		String orderNo = this.parseOrderNo(message);
		if (!StringUtils.hasText(orderNo)) {
			return;
		}
		log.info("收到充值支付成功消息，充值订单号：{}", orderNo);
		try {
			rechargeInfoService.handleRechargePaid(orderNo);
		} catch (Exception e) {
			log.error("充值到账处理失败，充值订单号：{}", orderNo, e);
		}
	}


	/**
	 * 解析消息体：支付模块发的是充值订单号字符串
	 *
	 * @param message 原始消息
	 * @return 充值订单号，解析失败返回 null
	 */
	private String parseOrderNo(Message message) {
		try {
			Object payload = messageConverter.fromMessage(message);
			if (payload instanceof String orderNo && StringUtils.hasText(orderNo)) {
				return orderNo;
			}
			log.error("充值支付成功消息体异常，实际类型：{}，内容：{}",
					payload == null ? "null" : payload.getClass().getName(),
					new String(message.getBody(), StandardCharsets.UTF_8));
			return null;
		} catch (Exception e) {
			log.error("充值支付成功消息体解析失败，消息内容：{}", new String(message.getBody(), StandardCharsets.UTF_8), e);
			return null;
		}
	}

}
