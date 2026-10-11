package com.atguigu.tingshu.user.mq;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 播放进度相关的消息发送器
 * <p>
 * 播放统计（播放量）的落库在专辑模块，用户模块只负责「用户听了」这件事的通知，
 * 两边靠 MQ 解耦：用户模块不直接写专辑库，专辑模块也不感知用户模块的表结构。
 * <p>
 * 消息不参与事务：播放进度已经写进 MongoDB，统计更新只是附带动作，
 * 统计丢一两条不影响听书主流程，所以发送失败只记日志、不向上抛
 */
@Slf4j
@Component
public class ListenProcessMqSender {

	@Autowired
	private RabbitService rabbitService;

	/**
	 * 上报一次播放：声音播放量与专辑播放量各加一
	 * <p>
	 * 前端每 10 秒上报一次播放进度，按当前约定不去重，每次上报都计数
	 *
	 * @param albumId 专辑ID
	 * @param trackId 声音ID
	 */
	public void sendPlayStat(Long albumId, Long trackId) {
		if (albumId == null || trackId == null) {
			return;
		}
		//同一次播放上报用同一个业务编号，方便在日志和 MQ 控制台里把两条消息串起来
		String businessNo = UUID.randomUUID().toString().replace("-", "");
		this.sendTrackStat(businessNo, albumId, trackId);
		this.sendAlbumStat(businessNo, albumId);
	}

	/**
	 * 发送声音播放量更新消息
	 *
	 * @param businessNo 业务编号
	 * @param albumId    专辑ID
	 * @param trackId    声音ID
	 */
	private void sendTrackStat(String businessNo, Long albumId, Long trackId) {
		TrackStatMqVo trackStatMqVo = new TrackStatMqVo();
		trackStatMqVo.setBusinessNo(businessNo);
		trackStatMqVo.setAlbumId(albumId);
		trackStatMqVo.setTrackId(trackId);
		trackStatMqVo.setStatType(SystemConstant.TRACK_STAT_PLAY);
		trackStatMqVo.setCount(1);
		this.doSend(MqConst.EXCHANGE_TRACK, MqConst.ROUTING_TRACK_STAT_UPDATE, trackStatMqVo);
	}

	/**
	 * 发送专辑播放量更新消息
	 *
	 * @param businessNo 业务编号
	 * @param albumId    专辑ID
	 */
	private void sendAlbumStat(String businessNo, Long albumId) {
		AlbumStatMqVo albumStatMqVo = new AlbumStatMqVo();
		albumStatMqVo.setBusinessNo(businessNo);
		albumStatMqVo.setAlbumId(albumId);
		albumStatMqVo.setStatType(SystemConstant.ALBUM_STAT_PLAY);
		albumStatMqVo.setCount(1);
		this.doSend(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_STAT_UPDATE, albumStatMqVo);
	}

	/**
	 * 发送消息：失败只记日志，不影响播放进度的保存
	 *
	 * @param exchange   交换机
	 * @param routingKey 路由键
	 * @param message    消息体
	 */
	private void doSend(String exchange, String routingKey, Object message) {
		try {
			rabbitService.sendMessage(exchange, routingKey, message);
			log.info("播放统计消息发送成功，路由键：{}，消息：{}", routingKey, message);
		} catch (Exception e) {
			log.error("播放统计消息发送失败，路由键：{}，消息：{}", routingKey, message, e);
		}
	}

}
