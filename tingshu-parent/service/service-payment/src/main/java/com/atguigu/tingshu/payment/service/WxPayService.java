package com.atguigu.tingshu.payment.service;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface WxPayService {

	/**
	 * 微信 JSAPI 下单（小程序内唤起支付）
	 * <p>
	 * 返回的是给 wx.requestPayment 用的支付参数，不是二维码；
	 * 金额由服务端从订单里取，前端传不了价格
	 *
	 * @param paymentType 支付类型：1301-订单 1302-充值
	 * @param orderNo     订单号
	 * @return 小程序支付参数（timeStamp、nonceStr、package、signType、paySign）
	 */
	Map<String, Object> createJsapi(String paymentType, String orderNo);


	/**
	 * 查询订单是否已支付（前端支付完成后轮询用）
	 *
	 * @param orderNo 订单号
	 * @return true-已支付
	 */
	Boolean queryPayStatus(String orderNo);


	/**
	 * 微信支付结果回调
	 * <p>
	 * 验签并解密通知报文，把支付记录置为已支付，再发消息通知订单模块发货。
	 * 返回给微信的报文必须是它约定的格式，否则微信会持续重推
	 *
	 * @param request 微信回调请求
	 * @return 应答报文
	 */
	String payNotify(HttpServletRequest request);

}
