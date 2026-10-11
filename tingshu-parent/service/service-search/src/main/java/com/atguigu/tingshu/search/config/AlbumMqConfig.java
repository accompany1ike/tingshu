package com.atguigu.tingshu.search.config;

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
 * 专辑索引相关的 MQ 配置
 * <p>
 * 专辑模块只管往交换机发消息，搜索模块只管监听队列，两边不互相依赖；
 * 交换机与队列在这里声明（重复声明同样的参数不会报错），这样即使 MQ 重启过也能自动恢复拓扑
 * <p>
 * 三条消息对应索引的三种维护动作：上架重建、下架删除、统计刷新
 */
@Configuration
public class AlbumMqConfig {

	/**
	 * 专辑交换机：direct 类型，路由键与队列名一一对应
	 */
	@Bean
	public DirectExchange albumExchange() {
		return new DirectExchange(MqConst.EXCHANGE_ALBUM, true, false);
	}

	/**
	 * 专辑上架队列：专辑新增、编辑、声音数量变化时收到消息
	 */
	@Bean
	public Queue albumUpperQueue() {
		return new Queue(MqConst.QUEUE_ALBUM_UPPER, true);
	}

	/**
	 * 专辑下架队列：专辑删除时收到消息
	 */
	@Bean
	public Queue albumLowerQueue() {
		return new Queue(MqConst.QUEUE_ALBUM_LOWER, true);
	}

	/**
	 * 专辑统计同步队列：播放量、订阅量、购买量、评论数变化时收到消息
	 */
	@Bean
	public Queue albumEsStatUpdateQueue() {
		return new Queue(MqConst.QUEUE_ALBUM_ES_STAT_UPDATE, true);
	}

	@Bean
	public Binding albumUpperBinding() {
		return BindingBuilder.bind(albumUpperQueue()).to(albumExchange()).with(MqConst.ROUTING_ALBUM_UPPER);
	}

	@Bean
	public Binding albumLowerBinding() {
		return BindingBuilder.bind(albumLowerQueue()).to(albumExchange()).with(MqConst.ROUTING_ALBUM_LOWER);
	}

	@Bean
	public Binding albumEsStatUpdateBinding() {
		return BindingBuilder.bind(albumEsStatUpdateQueue()).to(albumExchange()).with(MqConst.ROUTING_ALBUM_ES_STAT_UPDATE);
	}

	/**
	 * 消息转换器：与 Spring AMQP 的默认转换器一致，
	 * 专辑模块发来的 Java 序列化 Long、控制台手工投递的纯文本都能转成可读的消息体
	 */
	@Bean
	public MessageConverter messageConverter() {
		return new SimpleMessageConverter();
	}

}
