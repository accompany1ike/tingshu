package com.atguigu.tingshu.user.listener;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.user.service.UserPaidService;
import com.atguigu.tingshu.vo.user.UserPayRecordMqVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 支付成功消息监听：给用户「发货」
 * <p>
 * 异常必须在这里吃掉：MQ 是自动确认模式，方法抛异常会让消息重新入队、无限重投把日志刷爆；
 * 而且发货本身已经按订单号幂等，重投也不会重复发货
 */
@Slf4j
@Component
@SuppressWarnings({"all"})
public class UserPayRecordListener {

	@Autowired
	private UserPaidService userPaidService;

	@Autowired
	private MessageConverter messageConverter;


	/**
	 * 订单支付成功：写已购专辑/已购声音记录、开通会员
	 *
	 * @param message 消息体为 UserPayRecordMqVo
	 */
	@RabbitListener(queues = MqConst.QUEUE_USER_PAY_RECORD)
	public void onUserPayRecord(Message message) {
		UserPayRecordMqVo userPayRecordMqVo = this.convert(message);
		if (userPayRecordMqVo == null) {
			return;
		}
		log.info("收到支付成功消息，订单号：{}，用户ID：{}，项目类型：{}",
				userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId(), userPayRecordMqVo.getItemType());
		try {
			userPaidService.savePaidRecord(userPayRecordMqVo);
		} catch (Exception e) {
			log.error("支付成功后发货失败，订单号：{}", userPayRecordMqVo.getOrderNo(), e);
		}
	}


	/**
	 * 把消息体还原成支付成功消息对象
	 * <p>
	 * 订单模块发的是 Java 序列化的对象，消息转换器能直接还原；
	 * 控制台手工投递 JSON 文本时也兼容（用同一套字段名）
	 *
	 * @param message 原始消息
	 * @return 支付成功消息，解析失败返回 null
	 */
	private UserPayRecordMqVo convert(Message message) {
		try {
			Object payload = messageConverter.fromMessage(message);
			if (payload == null) {
				log.error("支付成功消息体为空，消息内容：{}", new String(message.getBody(), StandardCharsets.UTF_8));
				return null;
			}
			if (payload instanceof UserPayRecordMqVo userPayRecordMqVo) {
				return userPayRecordMqVo;
			}
			log.error("支付成功消息体类型不符，实际类型：{}", payload.getClass().getName());
			return null;
		} catch (Exception e) {
			log.error("支付成功消息体解析失败，消息内容：{}", new String(message.getBody(), StandardCharsets.UTF_8), e);
			return null;
		}
	}

}
