package com.atguigu.tingshu.user.config;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 用户相关的 MQ 配置
 * <p>
 * 按项目约定由消费方声明拓扑：本模块消费订单模块的「支付成功」消息，
 * 所以交换机和队列都在这里声明，订单模块只负责发
 */
@Configuration
public class UserMqConfig {

	/**
	 * 用户交换机：订单支付成功消息发到这里
	 */
	@Bean
	public DirectExchange userExchange() {
		return new DirectExchange(MqConst.EXCHANGE_USER, true, false);
	}

	/**
	 * 支付记录队列：消费后写已购专辑/已购声音、开通会员
	 */
	@Bean
	public Queue userPayRecordQueue() {
		return new Queue(MqConst.QUEUE_USER_PAY_RECORD, true);
	}

	@Bean
	public Binding userPayRecordBinding() {
		return BindingBuilder.bind(userPayRecordQueue()).to(userExchange()).with(MqConst.ROUTING_USER_PAY_RECORD);
	}

	/**
	 * 消息转换器：与项目其它模块保持一致，支持 Java 序列化的 POJO
	 */
	@Bean
	public MessageConverter messageConverter() {
		return new SimpleMessageConverter();
	}

}
