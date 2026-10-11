package com.atguigu.tingshu.album.mq;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 专辑索引消息发送器
 * <p>
 * 专辑（索引）数据在专辑库，检索在搜索模块，两边靠 MQ 解耦：
 * 专辑新增/编辑/删除、声音数量变化时通知搜索模块重建或删除索引，
 * 播放/订阅/购买/评论的统计累加后通知搜索模块刷新索引里的统计值。
 * <p>
 * 消息统一在「事务提交后」发送：搜索模块收到消息会回调专辑模块查库，
 * 如果事务还没提交，它查到的就是旧数据，索引会跟库不一致
 */
@Slf4j
@Component
public class AlbumIndexMqSender {

	@Autowired
	private RabbitService rabbitService;

	/**
	 * 通知搜索模块建立（重建）专辑索引
	 * <p>
	 * 新增专辑、编辑专辑、专辑下声音数量变化时调用
	 *
	 * @param albumId 专辑ID
	 */
	public void sendUpperAlbum(Long albumId) {
		this.sendAfterCommit(MqConst.ROUTING_ALBUM_UPPER, albumId);
	}

	/**
	 * 通知搜索模块删除专辑索引
	 * <p>
	 * 删除专辑（下架）时调用
	 *
	 * @param albumId 专辑ID
	 */
	public void sendLowerAlbum(Long albumId) {
		this.sendAfterCommit(MqConst.ROUTING_ALBUM_LOWER, albumId);
	}

	/**
	 * 通知搜索模块刷新索引里的专辑统计与热度
	 * <p>
	 * 播放、订阅、购买、评论的累加只落在专辑库，索引里的统计值是「建索引那一刻」的快照。
	 * 不同步的话，排行榜按播放量/订阅量排出来的一直是旧数据，
	 * 而榜单最看重的恰恰是这些数字的实时性。
	 *
	 * @param albumId 专辑ID
	 */
	public void sendAlbumStatToEs(Long albumId) {
		this.sendAfterCommit(MqConst.ROUTING_ALBUM_ES_STAT_UPDATE, albumId);
	}

	/**
	 * 事务提交后发送消息；当前没有事务时直接发送
	 *
	 * @param routingKey 路由键
	 * @param albumId    专辑ID
	 */
	private void sendAfterCommit(String routingKey, Long albumId) {
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					doSend(routingKey, albumId);
				}
			});
			return;
		}
		this.doSend(routingKey, albumId);
	}

	/**
	 * 发送消息：索引没建上不影响专辑本身的业务，所以失败只记日志、不向上抛
	 *
	 * @param routingKey 路由键
	 * @param albumId    专辑ID
	 */
	private void doSend(String routingKey, Long albumId) {
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, routingKey, albumId);
			log.info("专辑索引消息发送成功，路由键：{}，专辑ID：{}", routingKey, albumId);
		} catch (Exception e) {
			log.error("专辑索引消息发送失败，路由键：{}，专辑ID：{}", routingKey, albumId, e);
		}
	}

}
