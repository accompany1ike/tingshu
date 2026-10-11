package com.atguigu.tingshu.payment.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.payment.service.WxPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "微信支付接口")
@RestController
@RequestMapping("api/payment")
@Slf4j
@SuppressWarnings({"all"})
public class WxPayApiController {

	@Autowired
	private WxPayService wxPayService;


	/**
	 * 微信 JSAPI 下单
	 * <p>
	 * 小程序端拿返回的参数去调 wx.requestPayment 唤起支付；
	 * 金额由服务端按订单号取，前端无法改价
	 *
	 * @param paymentType 支付类型：1301-订单 1302-充值
	 * @param orderNo     订单号
	 * @return 小程序支付参数
	 */
	@Operation(summary = "微信JSAPI下单")
	@PostMapping("/wxPay/createJsapi/{paymentType}/{orderNo}")
	public Result<Map<String, Object>> createJsapi(@PathVariable String paymentType,
												   @PathVariable String orderNo) {
		return Result.ok(wxPayService.createJsapi(paymentType, orderNo));
	}


	/**
	 * 查询订单支付状态
	 * <p>
	 * 小程序支付完成后轮询这个接口，返回 true 才会跳「支付成功」页；
	 * 前端会把 loading 关掉，所以这里不需要弹加载框
	 *
	 * @param orderNo 订单号
	 * @return true-已支付
	 */
	@Operation(summary = "查询订单支付状态")
	@GetMapping("/wxPay/queryPayStatus/{orderNo}")
	public Result<Boolean> queryPayStatus(@PathVariable String orderNo) {
		return Result.ok(wxPayService.queryPayStatus(orderNo));
	}


	/**
	 * 微信支付结果回调
	 * <p>
	 * 这个地址要配在 Nacos 的 wechat.v3pay.notify-url 上，且必须能被微信公网访问；
	 * 返回体是微信约定的原始 JSON，不能包成 Result，否则微信会一直重推
	 * <p>
	 * 同时映射 /wxPay 和 /wxpay 两种写法：Spring MVC 路径区分大小写，
	 * 而 Nacos 上 notify-url 配的是小写 /api/payment/wxpay/notify，
	 * 小程序端调的是大写 /api/payment/wxPay/...，两边写法不一致，少一个都会 404
	 *
	 * @param request 微信回调请求
	 * @return 微信约定的应答报文
	 */
	@Operation(summary = "微信支付结果回调")
	@PostMapping({"/wxPay/notify", "/wxpay/notify"})
	public String payNotify(HttpServletRequest request) {
		return wxPayService.payNotify(request);
	}

}
