package com.atguigu.tingshu.payment.service;

import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;

public interface PaymentInfoService extends IService<PaymentInfo> {

	/**
	 * 创建支付记录（下单时调用）
	 * <p>
	 * 微信支付要求商户订单号唯一，所以一条订单只允许存在一条支付记录：
	 * 重复发起支付时复用已有记录，只刷新金额与内容，不再插一条
	 *
	 * @param paymentType 支付类型：1301-订单 1302-充值
	 * @param orderNo     订单号
	 * @param userId      用户ID
	 * @param amount      支付金额
	 * @param content     交易内容
	 * @return 支付记录
	 */
	PaymentInfo savePaymentInfo(String paymentType, String orderNo, Long userId, BigDecimal amount, String content);


	/**
	 * 根据订单号查询支付记录
	 *
	 * @param orderNo 订单号
	 * @return 支付记录，不存在时返回 null
	 */
	PaymentInfo getPaymentInfo(String orderNo);


	/**
	 * 把支付记录标记为已支付
	 * <p>
	 * 微信回调可能重复推送，所以这里必须幂等：只有「未支付 -> 已支付」这一次会返回 true，
	 * 调用方据此决定要不要发后续的业务消息
	 *
	 * @param orderNo         订单号
	 * @param outTradeNo      微信交易编号
	 * @param callbackContent 回调原文
	 * @return true-本次真的完成了状态流转；false-已经是已支付状态
	 */
	boolean updatePaymentInfoPaid(String orderNo, String outTradeNo, String callbackContent);

}
