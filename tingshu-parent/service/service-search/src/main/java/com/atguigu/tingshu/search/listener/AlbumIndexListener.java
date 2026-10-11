package com.atguigu.tingshu.search.listener;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.search.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 专辑索引消息监听：把专辑模块的业务变更翻译成索引库的增、删、改
 * <p>
 * 消息体就是专辑ID。专辑模块用 RabbitService 发送的是 Java 序列化的 Long，
 * 这里统一按「数字 -> 字节流 -> 文本」的顺序解析，控制台手工投递纯文本也能正常工作。
 * <p>
 * 异常必须在这里吃掉：MQ 是自动确认模式，方法抛异常会让消息重新入队、无限重投把日志刷爆
 */
@Slf4j
@Component
@SuppressWarnings({"all"})
public class AlbumIndexListener {

	@Autowired
	private SearchService searchService;

	@Autowired
	private MessageConverter messageConverter;

	/**
	 * 专辑上架：新增、编辑专辑，或专辑下声音数量变化时建立（刷新）索引
	 *
	 * @param message 消息体为专辑ID
	 */
	@RabbitListener(queues = MqConst.QUEUE_ALBUM_UPPER)
	public void onAlbumUpper(Message message) {
		Long albumId = this.parseAlbumId(message);
		log.info("收到专辑上架消息，专辑ID：{}", albumId);
		if (albumId == null) {
			return;
		}
		try {
			searchService.upperAlbum(albumId);
		} catch (Exception e) {
			log.error("专辑建立索引失败，专辑ID：{}", albumId, e);
		}
	}

	/**
	 * 专辑下架：专辑删除时把索引删掉
	 *
	 * @param message 消息体为专辑ID
	 */
	@RabbitListener(queues = MqConst.QUEUE_ALBUM_LOWER)
	public void onAlbumLower(Message message) {
		Long albumId = this.parseAlbumId(message);
		log.info("收到专辑下架消息，专辑ID：{}", albumId);
		if (albumId == null) {
			return;
		}
		try {
			searchService.lowerAlbum(albumId);
		} catch (Exception e) {
			log.error("专辑删除索引失败，专辑ID：{}", albumId, e);
		}
	}

	/**
	 * 专辑统计变化：刷新索引里的统计值与热度
	 * <p>
	 * 播放上报很密集，这里只改统计字段和热度，不整条重建索引
	 * （重建会连带给搜索建议库重写一遍，代价远大于收益）
	 *
	 * @param message 消息体为专辑ID
	 */
	@RabbitListener(queues = MqConst.QUEUE_ALBUM_ES_STAT_UPDATE)
	public void onAlbumEsStatUpdate(Message message) {
		Long albumId = this.parseAlbumId(message);
		if (albumId == null) {
			return;
		}
		try {
			searchService.updateAlbumStatIndex(albumId);
		} catch (Exception e) {
			log.error("刷新专辑索引统计失败，专辑ID：{}", albumId, e);
		}
	}

	/**
	 * 从消息里解析专辑ID
	 *
	 * @param message MQ消息
	 * @return 专辑ID，消息体不是合法ID时返回 null
	 */
	private Long parseAlbumId(Message message) {
		try {
			Object payload = messageConverter.fromMessage(message);
			if (payload instanceof Number number) {
				return number.longValue();
			}
			if (payload instanceof byte[] bytes) {
				return Long.parseLong(new String(bytes, StandardCharsets.UTF_8).trim());
			}
			return Long.parseLong(String.valueOf(payload).trim());
		} catch (Exception e) {
			log.error("专辑索引消息内容不是合法的专辑ID，消息体：{}", new String(message.getBody(), StandardCharsets.UTF_8), e);
			return null;
		}
	}

}
