package com.atguigu.tingshu.payment.service.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.payment.mapper.PaymentInfoMapper;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentInfoService {

	@Autowired
	private PaymentInfoMapper paymentInfoMapper;


	/**
	 * 创建支付记录（下单时调用）
	 *
	 * @param paymentType 支付类型
	 * @param orderNo     订单号
	 * @param userId      用户ID
	 * @param amount      支付金额
	 * @param content     交易内容
	 * @return 支付记录
	 */
	@Override
	public PaymentInfo savePaymentInfo(String paymentType, String orderNo, Long userId, BigDecimal amount, String content) {
		PaymentInfo exists = this.getPaymentInfo(orderNo);
		if (exists != null) {
			//用户点了两次「立即支付」：复用原记录，只刷新金额与内容，保持订单号唯一
			exists.setAmount(amount);
			exists.setContent(content);
			exists.setUserId(userId);
			paymentInfoMapper.updateById(exists);
			log.info("复用已有支付记录，订单号：{}，支付类型：{}", orderNo, paymentType);
			return exists;
		}

		PaymentInfo paymentInfo = new PaymentInfo();
		paymentInfo.setUserId(userId);
		paymentInfo.setPaymentType(paymentType);
		paymentInfo.setOrderNo(orderNo);
		paymentInfo.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
		paymentInfo.setAmount(amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP));
		paymentInfo.setContent(content);
		paymentInfo.setPaymentStatus(SystemConstant.PAYMENT_STATUS_UNPAID);
		paymentInfoMapper.insert(paymentInfo);
		log.info("创建支付记录成功，订单号：{}，支付类型：{}，金额：{}", orderNo, paymentType, amount);
		return paymentInfo;
	}


	/**
	 * 根据订单号查询支付记录
	 *
	 * @param orderNo 订单号
	 * @return 支付记录，不存在时返回 null
	 */
	@Override
	public PaymentInfo getPaymentInfo(String orderNo) {
		if (!StringUtils.hasText(orderNo)) {
			return null;
		}
		return this.getOne(new LambdaQueryWrapper<PaymentInfo>()
				.eq(PaymentInfo::getOrderNo, orderNo), false);
	}


	/**
	 * 把支付记录标记为已支付
	 *
	 * @param orderNo         订单号
	 * @param outTradeNo      微信交易编号
	 * @param callbackContent 回调原文
	 * @return true-本次真的完成了状态流转；false-已经是已支付状态
	 */
	@Override
	public boolean updatePaymentInfoPaid(String orderNo, String outTradeNo, String callbackContent) {
		//条件更新：带上「原状态=未支付」，并发或重复回调只有一个能改成功
		return this.update(new LambdaUpdateWrapper<PaymentInfo>()
				.set(PaymentInfo::getPaymentStatus, SystemConstant.PAYMENT_STATUS_PAID)
				.set(PaymentInfo::getOutTradeNo, outTradeNo)
				.set(PaymentInfo::getCallbackContent, callbackContent)
				.set(PaymentInfo::getCallbackTime, new Date())
				.eq(PaymentInfo::getOrderNo, orderNo)
				.eq(PaymentInfo::getPaymentStatus, SystemConstant.PAYMENT_STATUS_UNPAID));
	}

}
