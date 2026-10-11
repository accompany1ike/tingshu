package com.atguigu.tingshu.album.config;

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
 * 播放统计相关的 MQ 配置
 * <p>
 * 用户模块只管往交换机发消息，专辑模块只管监听队列，两边不互相依赖；
 * 交换机与队列在这里声明（重复声明同样的参数不会报错），这样即使 MQ 重启过也能自动恢复拓扑
 */
@Configuration
public class StatMqConfig {

	/**
	 * 专辑交换机：播放量变更消息发到这里
	 * <p>
	 * 与搜索模块声明的同名交换机参数完全一致，两个服务各声明一次不会冲突
	 */
	@Bean
	public DirectExchange albumStatExchange() {
		return new DirectExchange(MqConst.EXCHANGE_ALBUM, true, false);
	}

	/**
	 * 声音交换机：声音维度的统计变更消息发到这里
	 */
	@Bean
	public DirectExchange trackStatExchange() {
		return new DirectExchange(MqConst.EXCHANGE_TRACK, true, false);
	}

	/**
	 * 专辑统计更新队列
	 */
	@Bean
	public Queue albumStatUpdateQueue() {
		return new Queue(MqConst.QUEUE_ALBUM_STAT_UPDATE, true);
	}

	/**
	 * 声音统计更新队列
	 */
	@Bean
	public Queue trackStatUpdateQueue() {
		return new Queue(MqConst.QUEUE_TRACK_STAT_UPDATE, true);
	}

	@Bean
	public Binding albumStatUpdateBinding() {
		return BindingBuilder.bind(albumStatUpdateQueue()).to(albumStatExchange()).with(MqConst.ROUTING_ALBUM_STAT_UPDATE);
	}

	@Bean
	public Binding trackStatUpdateBinding() {
		return BindingBuilder.bind(trackStatUpdateQueue()).to(trackStatExchange()).with(MqConst.ROUTING_TRACK_STAT_UPDATE);
	}

	/**
	 * 消息转换器：与 Spring AMQP 的默认转换器一致。
	 * 用户模块用 RabbitService 发送的是 Java 序列化的消息对象，
	 * 这里保持默认实现，控制台手工投递的 JSON 文本也能被监听器兜底解析
	 */
	@Bean
	public MessageConverter messageConverter() {
		return new SimpleMessageConverter();
	}

}
