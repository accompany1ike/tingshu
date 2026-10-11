package com.atguigu.tingshu.order.mq;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.user.UserPayRecordMqVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 订单模块的消息发送器
 * <p>
 * 订单只管落订单库，用户模块的已购记录、专辑库的购买量都靠消息通知对方自己写，
 * 不跨库直连别人的表。
 * <p>
 * 发送失败只记日志不向上抛：支付成功的主流程是「改订单状态」，改完就已经算成功了，
 * 后续通知失败不该让支付回调报错（回调报错会触发微信重复推送）
 */
@Slf4j
@Component
public class OrderMqSender {

	@Autowired
	private RabbitService rabbitService;


	/**
	 * 通知用户模块「发货」：写已购声音/已购专辑记录、开通会员
	 *
	 * @param userPayRecordMqVo 支付成功消息
	 */
	public void sendUserPayRecord(UserPayRecordMqVo userPayRecordMqVo) {
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_USER, MqConst.ROUTING_USER_PAY_RECORD, userPayRecordMqVo);
			log.info("支付成功消息发送成功，订单号：{}，用户ID：{}", userPayRecordMqVo.getOrderNo(), userPayRecordMqVo.getUserId());
		} catch (Exception e) {
			log.error("支付成功消息发送失败，订单号：{}", userPayRecordMqVo.getOrderNo(), e);
		}
	}


	/**
	 * 通知专辑模块把专辑购买量加一
	 * <p>
	 * 购买量是排行榜的一个维度，只累加不重算，所以一条订单只能发一次
	 *
	 * @param albumId 专辑ID
	 */
	public void sendAlbumBuyStat(Long albumId) {
		if (albumId == null) {
			return;
		}
		AlbumStatMqVo albumStatMqVo = new AlbumStatMqVo();
		albumStatMqVo.setBusinessNo(UUID.randomUUID().toString().replace("-", ""));
		albumStatMqVo.setAlbumId(albumId);
		albumStatMqVo.setStatType(SystemConstant.ALBUM_STAT_BUY);
		albumStatMqVo.setCount(1);
		try {
			rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_STAT_UPDATE, albumStatMqVo);
			log.info("专辑购买量消息发送成功，专辑ID：{}", albumId);
		} catch (Exception e) {
			log.error("专辑购买量消息发送失败，专辑ID：{}", albumId, e);
		}
	}


	/**
	 * 发送「超时取消订单」的延迟消息
	 * <p>
	 * 消息先投到延迟中转队列（带 TTL），到期后由死信路由转到真正的取消队列，
	 * 走默认交换机直接按队列名路由，不额外声明延迟交换机
	 *
	 * @param orderNo 订单号
	 */
	public void sendCancelOrderDelay(String orderNo) {
		try {
			rabbitService.sendMessage("", MqConst.QUEUE_CANCEL_ORDER_DELAY, orderNo);
			log.info("超时取消订单延迟消息发送成功，订单号：{}", orderNo);
		} catch (Exception e) {
			log.error("超时取消订单延迟消息发送失败，订单号：{}", orderNo, e);
		}
	}

}
