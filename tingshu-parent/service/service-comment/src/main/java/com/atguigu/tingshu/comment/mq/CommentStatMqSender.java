package com.atguigu.tingshu.comment.mq;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 评论统计消息发送器
 * <p>
 * 评论存在评论库（MongoDB），专辑评论数存在专辑库，两边靠 MQ 解耦：
 * 评论模块不直接写专辑库，专辑模块也不感知评论的数据结构。
 * <p>
 * 消息复用播放统计那条路由（{@code tingshu.album.stat.update}），
 * 专辑模块的监听器本来就按 statType 分流，评论数（0404）走同一个入口即可，
 * 统计落库后会自动触发索引里的统计刷新，排行榜的「评论数」维度也就跟着动了
 */
@Slf4j
@Component
public class CommentStatMqSender {

	@Autowired
	private RabbitService rabbitService;

	/**
	 * 通知专辑模块更新专辑评论数
	 * <p>
	 * 新增评论传正数、删除评论传负数；发送失败只记日志——
	 * 评论已经存进 MongoDB 了，统计丢一两条不影响主流程
	 *
	 * @param albumId 专辑ID
	 * @param count   增量：新增为正、删除为负
	 */
	public void sendCommentStat(Long albumId, int count) {
		if (albumId == null || count == 0) {
			return;
		}
		AlbumStatMqVo albumStatMqVo = new AlbumStatMqVo();
		albumStatMqVo.setBusinessNo(UUID.randomUUID().toString().replace("-", ""));
		albumStatMqVo.setAlbumId(albumId);
		albumStatMqVo.setStatType(SystemConstant.ALBUM_STAT_COMMENT);
		albumStatMqVo.setCount(count);
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_STAT_UPDATE, albumStatMqVo);
			log.info("专辑评论数消息发送成功，专辑ID：{}，增量：{}", albumId, count);
		} catch (Exception e) {
			log.error("专辑评论数消息发送失败，专辑ID：{}，增量：{}", albumId, count, e);
		}
	}

}
