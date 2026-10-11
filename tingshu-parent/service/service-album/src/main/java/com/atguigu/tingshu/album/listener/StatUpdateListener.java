package com.atguigu.tingshu.album.listener;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 播放统计消息监听：把用户模块的播放上报翻译成 album_stat / track_stat 的累加
 * <p>
 * 用户模块每 10 秒上报一次播放进度，上报时不阻塞等统计落库，
 * 统计更新全部在这里异步完成，播放接口的响应时间不受专辑库写入影响。
 * <p>
 * 异常必须在这里吃掉：MQ 是自动确认模式，方法抛异常会让消息重新入队、无限重投把日志刷爆
 */
@Slf4j
@Component
@SuppressWarnings({"all"})
public class StatUpdateListener {

	@Autowired
	private AlbumInfoService albumInfoService;

	@Autowired
	private TrackInfoService trackInfoService;

	@Autowired
	private MessageConverter messageConverter;


	/**
	 * 专辑统计更新：专辑播放量、订阅量、购买量、评论数
	 *
	 * @param message 消息体为 AlbumStatMqVo
	 */
	@RabbitListener(queues = MqConst.QUEUE_ALBUM_STAT_UPDATE)
	public void onAlbumStatUpdate(Message message) {
		AlbumStatMqVo albumStatMqVo = this.convert(message, AlbumStatMqVo.class);
		if (albumStatMqVo == null) {
			return;
		}
		log.info("收到专辑统计更新消息，专辑ID：{}，统计类型：{}，增量：{}",
				albumStatMqVo.getAlbumId(), albumStatMqVo.getStatType(), albumStatMqVo.getCount());
		try {
			albumInfoService.updateAlbumStat(albumStatMqVo);
		} catch (Exception e) {
			log.error("专辑统计更新失败，专辑ID：{}，统计类型：{}",
					albumStatMqVo.getAlbumId(), albumStatMqVo.getStatType(), e);
		}
	}


	/**
	 * 声音统计更新：声音播放量、收藏量、点赞量、评论数
	 *
	 * @param message 消息体为 TrackStatMqVo
	 */
	@RabbitListener(queues = MqConst.QUEUE_TRACK_STAT_UPDATE)
	public void onTrackStatUpdate(Message message) {
		TrackStatMqVo trackStatMqVo = this.convert(message, TrackStatMqVo.class);
		if (trackStatMqVo == null) {
			return;
		}
		log.info("收到声音统计更新消息，声音ID：{}，统计类型：{}，增量：{}",
				trackStatMqVo.getTrackId(), trackStatMqVo.getStatType(), trackStatMqVo.getCount());
		try {
			trackInfoService.updateTrackStat(trackStatMqVo);
		} catch (Exception e) {
			log.error("声音统计更新失败，声音ID：{}，统计类型：{}",
					trackStatMqVo.getTrackId(), trackStatMqVo.getStatType(), e);
		}
	}


	/**
	 * 把消息体还原成统计对象
	 * <p>
	 * 用户模块发送的是 Java 序列化的对象，消息转换器能直接还原；
	 * 控制台手工投递 JSON 文本时走文本解析分支，方便联调
	 *
	 * @param message 原始消息
	 * @param clazz   目标类型
	 * @return 统计对象，解析失败返回 null
	 */
	private <T> T convert(Message message, Class<T> clazz) {
		try {
			Object payload = messageConverter.fromMessage(message);
			if (payload == null) {
				log.error("统计消息体为空，消息内容：{}", new String(message.getBody(), StandardCharsets.UTF_8));
				return null;
			}
			if (clazz.isInstance(payload)) {
				return clazz.cast(payload);
			}
			if (payload instanceof String text) {
				return JSON.parseObject(text, clazz);
			}
			if (payload instanceof byte[] bytes) {
				return JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), clazz);
			}
			return JSON.parseObject(JSON.toJSONString(payload), clazz);
		} catch (Exception e) {
			log.error("统计消息体解析失败，消息内容：{}", new String(message.getBody(), StandardCharsets.UTF_8), e);
			return null;
		}
	}

}
