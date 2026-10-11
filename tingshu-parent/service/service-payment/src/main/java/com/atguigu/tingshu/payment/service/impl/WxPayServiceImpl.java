package com.atguigu.tingshu.payment.service.impl;

import com.atguigu.tingshu.account.AccountFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.payment.PaymentInfo;
import com.atguigu.tingshu.order.client.OrderFeignClient;
import com.atguigu.tingshu.payment.config.WxPayV3Config;
import com.atguigu.tingshu.payment.mq.WxPayMqSender;
import com.atguigu.tingshu.payment.service.PaymentInfoService;
import com.atguigu.tingshu.payment.service.WxPayService;
import com.atguigu.tingshu.payment.util.PayUtil;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.wechat.pay.java.core.Config;
import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.http.DefaultHttpClientBuilder;
import com.wechat.pay.java.core.http.HttpClient;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.core.notification.RequestParam;
import com.wechat.pay.java.service.payments.jsapi.JsapiServiceExtension;
import com.wechat.pay.java.service.payments.jsapi.model.Amount;
import com.wechat.pay.java.service.payments.jsapi.model.Payer;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayRequest;
import com.wechat.pay.java.service.payments.jsapi.model.PrepayWithRequestPaymentResponse;
import com.wechat.pay.java.service.payments.model.Transaction;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class WxPayServiceImpl implements WxPayService {

	/** 微信支付成功的交易状态 */
	private static final Transaction.TradeStateEnum TRADE_STATE_SUCCESS = Transaction.TradeStateEnum.SUCCESS;

	/** 微信回调成功应答，格式必须与官方约定一致，否则微信会持续重推 */
	private static final String NOTIFY_SUCCESS = "{\"code\":\"SUCCESS\",\"message\":\"成功\"}";

	/** 充值单的交易内容（微信订单描述） */
	private static final String RECHARGE_CONTENT = "账户充值";

	@Autowired
	private PaymentInfoService paymentInfoService;

	@Autowired
	private OrderFeignClient orderFeignClient;

	@Autowired
	private AccountFeignClient accountFeignClient;

	@Autowired
	private UserFeignClient userFeignClient;

	@Autowired
	private WxPayV3Config wxPayV3Config;

	@Autowired
	private RSAAutoCertificateConfig rsaAutoCertificateConfig;

	@Autowired
	private WxPayMqSender wxPayMqSender;

	/**
	 * JSAPI 下单客户端
	 * <p>
	 * 懒加载并缓存：SDK 的 Builder 内部会建一个 OkHttp 连接池，
	 * 每次下单都新建会白扔连接池；同时避免在字段初始化阶段就去读商户证书
	 */
	private volatile JsapiServiceExtension jsapiServiceExtension;


	/**
	 * 微信 JSAPI 下单（小程序内唤起支付）
	 *
	 * @param paymentType 支付类型：1301-订单 1302-充值
	 * @param orderNo     订单号
	 * @return 小程序支付参数
	 */
	@Override
	public Map<String, Object> createJsapi(String paymentType, String orderNo) {
		Long userId = AuthContextHolder.getUserId();
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (!StringUtils.hasText(orderNo)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "订单号不能为空");
		}

		//1.按支付类型取「应付金额 + 交易内容」：金额一律以业务库为准，前端传不了价格
		BigDecimal payAmount;
		String content;
		if (SystemConstant.PAYMENT_TYPE_ORDER.equals(paymentType)) {
			OrderInfo orderInfo = this.getPayableOrder(orderNo, userId);
			payAmount = orderInfo.getOrderAmount();
			content = orderInfo.getOrderTitle();
		} else if (SystemConstant.PAYMENT_TYPE_RECHARGE.equals(paymentType)) {
			RechargeInfo rechargeInfo = this.getPayableRecharge(orderNo, userId);
			payAmount = rechargeInfo.getRechargeAmount();
			content = RECHARGE_CONTENT;
		} else {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "暂不支持的支付类型");
		}
		if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "支付金额异常，无法发起支付");
		}

		//2.落支付记录：同一单重复发起支付只刷新，不会插出两条
		PaymentInfo paymentInfo = paymentInfoService.savePaymentInfo(paymentType, orderNo, userId, payAmount, content);

		//3.取用户 openid：JSAPI 支付必须带 payer.openid
		UserInfoVo userInfoVo = this.getData(userFeignClient.getUserInfo(userId));
		if (userInfoVo == null || !StringUtils.hasText(userInfoVo.getWxOpenId())) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "未获取到微信授权信息，请重新登录");
		}

		//4.调微信统一下单，拿到可直接给 wx.requestPayment 用的支付参数
		PrepayRequest prepayRequest = new PrepayRequest();
		prepayRequest.setAppid(wxPayV3Config.getAppid());
		prepayRequest.setMchid(wxPayV3Config.getMerchantId());
		prepayRequest.setDescription(this.buildDescription(paymentInfo.getContent(), orderNo));
		prepayRequest.setOutTradeNo(orderNo);
		prepayRequest.setNotifyUrl(wxPayV3Config.getNotifyUrl());
		Amount amount = new Amount();
		//微信金额单位是分，业务金额是元
		amount.setTotal(payAmount.multiply(BigDecimal.valueOf(100))
				.setScale(0, RoundingMode.HALF_UP)
				.intValue());
		prepayRequest.setAmount(amount);
		Payer payer = new Payer();
		payer.setOpenid(userInfoVo.getWxOpenId());
		prepayRequest.setPayer(payer);

		PrepayWithRequestPaymentResponse response = this.getJsapiServiceExtension()
				.prepayWithRequestPayment(prepayRequest);

		Map<String, Object> payParams = new HashMap<>(8);
		payParams.put("appId", response.getAppId());
		payParams.put("timeStamp", response.getTimeStamp());
		payParams.put("nonceStr", response.getNonceStr());
		//注意字段名：SDK 叫 packageVal，小程序端 wx.requestPayment 要的是 package
		payParams.put("package", response.getPackageVal());
		payParams.put("signType", response.getSignType());
		payParams.put("paySign", response.getPaySign());
		log.info("微信JSAPI下单成功，支付类型：{}，单号：{}，金额：{}", paymentType, orderNo, payAmount);
		return payParams;
	}


	/**
	 * 取待支付的订单，并校验归属与状态
	 *
	 * @param orderNo 订单号
	 * @param userId  当前登录用户ID
	 * @return 订单
	 */
	private OrderInfo getPayableOrder(String orderNo, Long userId) {
		OrderInfo orderInfo = this.getData(orderFeignClient.getOrderInfo(orderNo));
		if (orderInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "订单不存在");
		}
		if (!orderInfo.getUserId().equals(userId)) {
			throw new GuiguException(ResultCodeEnum.PERMISSION.getCode(), "无权支付该订单");
		}
		if (!SystemConstant.ORDER_STATUS_UNPAID.equals(orderInfo.getOrderStatus())) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "订单不是待支付状态");
		}
		return orderInfo;
	}


	/**
	 * 取待支付的充值单，并校验归属与状态
	 *
	 * @param orderNo 充值订单号
	 * @param userId  当前登录用户ID
	 * @return 充值单
	 */
	private RechargeInfo getPayableRecharge(String orderNo, Long userId) {
		RechargeInfo rechargeInfo = this.getData(accountFeignClient.getRechargeInfo(orderNo));
		if (rechargeInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "充值单不存在");
		}
		if (!rechargeInfo.getUserId().equals(userId)) {
			throw new GuiguException(ResultCodeEnum.PERMISSION.getCode(), "无权支付该充值单");
		}
		if (!SystemConstant.RECHARGE_STATUS_UNPAID.equals(rechargeInfo.getRechargeStatus())) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "充值单不是待支付状态");
		}
		return rechargeInfo;
	}


	/**
	 * 查询订单是否已支付（前端支付完成后轮询用）
	 * <p>
	 * 直接读支付记录，不去问微信：回调是微信主动推的，本地记录一旦置为已支付就是最终态
	 *
	 * @param orderNo 订单号
	 * @return true-已支付
	 */
	@Override
	public Boolean queryPayStatus(String orderNo) {
		PaymentInfo paymentInfo = paymentInfoService.getPaymentInfo(orderNo);
		if (paymentInfo == null) {
			return false;
		}
		return SystemConstant.PAYMENT_STATUS_PAID.equals(paymentInfo.getPaymentStatus());
	}


	/**
	 * 微信支付结果回调
	 *
	 * @param request 微信回调请求
	 * @return 应答报文
	 */
	@Override
	public String payNotify(HttpServletRequest request) {
		String body;
		try {
			body = PayUtil.readData(request);
		} catch (Exception e) {
			log.error("读取微信回调报文失败", e);
			return this.notifyFail("读取回调报文失败");
		}

		//1.验签 + 解密：签名不对说明不是微信发来的，直接拒掉
		Transaction transaction;
		try {
			RequestParam requestParam = new RequestParam.Builder()
					.serialNumber(request.getHeader("Wechatpay-Serial"))
					.timestamp(request.getHeader("Wechatpay-Timestamp"))
					.nonce(request.getHeader("Wechatpay-Nonce"))
					.signature(request.getHeader("Wechatpay-Signature"))
					.signType(request.getHeader("Wechatpay-SignType"))
					.body(body)
					.build();
			transaction = new NotificationParser(rsaAutoCertificateConfig)
					.parse(requestParam, Transaction.class);
		} catch (Exception e) {
			log.error("微信回调验签/解密失败，报文：{}", body, e);
			return this.notifyFail("回调验签失败");
		}

		String orderNo = transaction.getOutTradeNo();
		log.info("收到微信支付回调，订单号：{}，交易状态：{}", orderNo, transaction.getTradeState());
		if (!TRADE_STATE_SUCCESS.equals(transaction.getTradeState())) {
			//非成功状态（比如用户取消）不改变业务状态，但也要应答成功，避免微信反复推送
			log.info("微信交易未成功，忽略，订单号：{}，状态：{}", orderNo, transaction.getTradeState());
			return NOTIFY_SUCCESS;
		}

		try {
			//2.先读支付记录：订单和充值的后续处理完全不同，回调里必须按支付类型分流
			PaymentInfo paymentInfo = paymentInfoService.getPaymentInfo(orderNo);
			if (paymentInfo == null) {
				log.error("支付成功但支付记录不存在，订单号：{}", orderNo);
				return this.notifyFail("支付记录不存在");
			}

			//3.幂等置为已支付：只有真的完成状态流转才发后续消息
			boolean changed = paymentInfoService.updatePaymentInfoPaid(
					orderNo, transaction.getTransactionId(), body);
			if (!changed) {
				log.info("支付记录已是已支付状态，跳过重复处理，订单号：{}", orderNo);
				return NOTIFY_SUCCESS;
			}

			//4.按支付类型通知下游：订单走发货链路，充值走账户入账链路
			if (SystemConstant.PAYMENT_TYPE_RECHARGE.equals(paymentInfo.getPaymentType())) {
				wxPayMqSender.sendRechargePaySuccess(orderNo);
			} else {
				wxPayMqSender.sendOrderPaySuccess(orderNo);
			}
		} catch (Exception e) {
			//处理失败要返回失败，让微信重推，否则这笔钱就没有后续发货了
			log.error("支付成功回调处理失败，订单号：{}", orderNo, e);
			return this.notifyFail("回调处理失败");
		}
		return NOTIFY_SUCCESS;
	}


	/**
	 * 取 JSAPI 下单客户端（双重检查加锁，只建一次）
	 *
	 * @return JSAPI 下单客户端
	 */
	private JsapiServiceExtension getJsapiServiceExtension() {
		if (jsapiServiceExtension == null) {
			synchronized (this) {
				if (jsapiServiceExtension == null) {
					Config config = rsaAutoCertificateConfig;
					HttpClient httpClient = new DefaultHttpClientBuilder().config(config).build();
					jsapiServiceExtension = new JsapiServiceExtension.Builder()
							.config(config)
							.httpClient(httpClient)
							.build();
				}
			}
		}
		return jsapiServiceExtension;
	}


	/**
	 * 组装商品描述：微信要求 1~127 个字符，过长会被拒
	 *
	 * @param content 订单标题
	 * @param orderNo 订单号
	 * @return 商品描述
	 */
	private String buildDescription(String content, String orderNo) {
		String description = StringUtils.hasText(content) ? content : "听书订单";
		if (description.length() > 100) {
			description = description.substring(0, 100);
		}
		return description;
	}


	private String notifyFail(String message) {
		return "{\"code\":\"FAIL\",\"message\":\"" + message + "\"}";
	}


	/**
	 * 远程调用结果统一取值：服务降级、业务异常时 data 都是 null，调用方按「没查到」处理
	 *
	 * @param result 远程调用结果
	 * @return 业务数据
	 */
	private <T> T getData(Result<T> result) {
		return result == null ? null : result.getData();
	}

}
