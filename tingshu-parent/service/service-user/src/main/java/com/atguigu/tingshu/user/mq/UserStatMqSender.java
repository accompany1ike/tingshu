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
 * 用户行为统计的消息发送器：订阅专辑、收藏声音
 * <p>
 * 订阅关系与收藏关系存在用户库（MongoDB），专辑订阅量与声音收藏量存在专辑库，
 * 用户模块不直接写别人的库，只发消息让专辑模块自己累加。
 * <p>
 * 发送失败只记日志：订阅/收藏关系已经落库，统计丢一两条不影响主流程，
 * 而且统计是增量累加，下次同类操作还能把数字带上来
 */
@Slf4j
@Component
public class UserStatMqSender {

	@Autowired
	private RabbitService rabbitService;

	/**
	 * 通知专辑模块更新专辑订阅量
	 *
	 * @param albumId 专辑ID
	 * @param count   增量：订阅为正、取消订阅为负
	 */
	public void sendSubscribeStat(Long albumId, int count) {
		if (albumId == null || count == 0) {
			return;
		}
		AlbumStatMqVo albumStatMqVo = new AlbumStatMqVo();
		albumStatMqVo.setBusinessNo(UUID.randomUUID().toString().replace("-", ""));
		albumStatMqVo.setAlbumId(albumId);
		albumStatMqVo.setStatType(SystemConstant.ALBUM_STAT_SUBSCRIBE);
		albumStatMqVo.setCount(count);
		this.doSendAlbumStat(albumStatMqVo);
	}


	/**
	 * 通知专辑模块更新声音收藏量
	 *
	 * @param albumId 专辑ID
	 * @param trackId 声音ID
	 * @param count   增量：收藏为正、取消收藏为负
	 */
	public void sendCollectStat(Long albumId, Long trackId, int count) {
		if (albumId == null || trackId == null || count == 0) {
			return;
		}
		TrackStatMqVo trackStatMqVo = new TrackStatMqVo();
		trackStatMqVo.setBusinessNo(UUID.randomUUID().toString().replace("-", ""));
		trackStatMqVo.setAlbumId(albumId);
		trackStatMqVo.setTrackId(trackId);
		trackStatMqVo.setStatType(SystemConstant.TRACK_STAT_COLLECT);
		trackStatMqVo.setCount(count);
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_TRACK, MqConst.ROUTING_TRACK_STAT_UPDATE, trackStatMqVo);
			log.info("声音收藏量消息发送成功，专辑ID：{}，声音ID：{}，增量：{}", albumId, trackId, count);
		} catch (Exception e) {
			log.error("声音收藏量消息发送失败，专辑ID：{}，声音ID：{}，增量：{}", albumId, trackId, count, e);
		}
	}


	/**
	 * 发送专辑维度统计消息
	 *
	 * @param albumStatMqVo 统计变更消息
	 */
	private void doSendAlbumStat(AlbumStatMqVo albumStatMqVo) {
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_STAT_UPDATE, albumStatMqVo);
			log.info("专辑订阅量消息发送成功，专辑ID：{}，增量：{}",
					albumStatMqVo.getAlbumId(), albumStatMqVo.getCount());
		} catch (Exception e) {
			log.error("专辑订阅量消息发送失败，专辑ID：{}，增量：{}",
					albumStatMqVo.getAlbumId(), albumStatMqVo.getCount(), e);
		}
	}

}
