package com.atguigu.tingshu.order.config;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 订单相关的 MQ 配置
 * <p>
 * 按项目约定由消费方声明拓扑：本模块消费「支付成功」「超时取消订单」两类消息，
 * 所以交换机和队列都在这里声明，生产方（支付模块）只管发
 */
@Configuration
public class OrderMqConfig {

	/**
	 * 订单交换机：支付模块的支付成功消息发到这里
	 */
	@Bean
	public DirectExchange orderExchange() {
		return new DirectExchange(MqConst.EXCHANGE_ORDER, true, false);
	}

	/**
	 * 取消订单交换机：延迟队列过期后把消息死信转到这里
	 */
	@Bean
	public DirectExchange cancelOrderExchange() {
		return new DirectExchange(MqConst.EXCHANGE_CANCEL_ORDER, true, false);
	}

	/**
	 * 支付成功队列
	 */
	@Bean
	public Queue orderPaySuccessQueue() {
		return new Queue(MqConst.QUEUE_ORDER_PAY_SUCCESS, true);
	}

	/**
	 * 取消订单队列：延迟队列的死信最终落到这里
	 */
	@Bean
	public Queue cancelOrderQueue() {
		return new Queue(MqConst.QUEUE_CANCEL_ORDER, true);
	}

	/**
	 * 取消订单的延迟中转队列
	 * <p>
	 * 消息进来先待 CANCEL_ORDER_DELAY_TIME 秒，过期后自动死信到
	 * EXCHANGE_CANCEL_ORDER + ROUTING_CANCEL_ORDER，也就是 cancelOrderQueue。
	 * 用 TTL + 死信而不是 x-delayed-message 插件，避免依赖服务端插件
	 */
	@Bean
	public Queue cancelOrderDelayQueue() {
		return QueueBuilder.durable(MqConst.QUEUE_CANCEL_ORDER_DELAY)
				.ttl(MqConst.CANCEL_ORDER_DELAY_TIME * 1000)
				.deadLetterExchange(MqConst.EXCHANGE_CANCEL_ORDER)
				.deadLetterRoutingKey(MqConst.ROUTING_CANCEL_ORDER)
				.build();
	}

	@Bean
	public Binding orderPaySuccessBinding() {
		return BindingBuilder.bind(orderPaySuccessQueue()).to(orderExchange()).with(MqConst.ROUTING_ORDER_PAY_SUCCESS);
	}

	@Bean
	public Binding cancelOrderBinding() {
		return BindingBuilder.bind(cancelOrderQueue()).to(cancelOrderExchange()).with(MqConst.ROUTING_CANCEL_ORDER);
	}

	/**
	 * 消息转换器：与项目其它模块保持一致，支持 Java 序列化的 POJO 与纯字符串
	 */
	@Bean
	public MessageConverter messageConverter() {
		return new SimpleMessageConverter();
	}

}
