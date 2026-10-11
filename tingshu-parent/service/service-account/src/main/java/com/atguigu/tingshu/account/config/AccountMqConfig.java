package com.atguigu.tingshu.account.config;

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
 * 账户相关的 MQ 配置
 * <p>
 * 按项目约定由消费方声明拓扑：本模块消费支付模块的「充值支付成功」消息，
 * 所以队列和绑定都在这里声明，支付模块只负责发
 */
@Configuration
public class AccountMqConfig {

	/**
	 * 订单交换机：充值支付成功消息和订单支付成功消息共用这个交换机，靠路由键区分
	 */
	@Bean
	public DirectExchange accountOrderExchange() {
		return new DirectExchange(MqConst.EXCHANGE_ORDER, true, false);
	}

	/**
	 * 充值支付成功队列：消费后把充值记录置为已支付并给账户加钱
	 */
	@Bean
	public Queue rechargePaySuccessQueue() {
		return new Queue(MqConst.QUEUE_RECHARGE_PAY_SUCCESS, true);
	}

	@Bean
	public Binding rechargePaySuccessBinding() {
		return BindingBuilder.bind(rechargePaySuccessQueue())
				.to(accountOrderExchange())
				.with(MqConst.ROUTING_RECHARGE_PAY_SUCCESS);
	}

	/**
	 * 消息转换器：与项目其它模块保持一致，支持 Java 序列化的 POJO 与纯字符串
	 */
	@Bean
	public MessageConverter messageConverter() {
		return new SimpleMessageConverter();
	}

}
