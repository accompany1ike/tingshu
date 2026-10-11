package com.atguigu.tingshu.order.api;

import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@Tag(name = "订单管理")
@RestController
@RequestMapping("api/order")
@SuppressWarnings({"all"})
public class OrderInfoApiController {

	@Autowired
	private OrderInfoService orderInfoService;


	/**
	 * 确认订单（详情页点「立即购买」后进确认订单页）
	 * <p>
	 * 前端只传「买什么」，金额由服务端现算并附上签名，确认订单页展示的就是最终要付的钱
	 *
	 * @param tradeVo 付款项目（itemType、itemId、trackCount）
	 * @return 待提交的订单信息
	 */
	@Operation(summary = "确认订单")
	@GuiGuLogin
	@PostMapping("/orderInfo/trade")
	public Result<OrderInfoVo> trade(@RequestBody @Validated TradeVo tradeVo) {
		return Result.ok(orderInfoService.trade(tradeVo));
	}


	/**
	 * 提交订单
	 * <p>
	 * 返回体固定为 {orderNo}：前端下一步要拿订单号去调微信下单接口
	 *
	 * @param orderInfoVo 确认订单返回的对象（原样带回）
	 * @return 订单号
	 */
	@Operation(summary = "提交订单")
	@GuiGuLogin
	@PostMapping("/orderInfo/submitOrder")
	public Result<Map<String, String>> submitOrder(@RequestBody OrderInfoVo orderInfoVo) {
		String orderNo = orderInfoService.submitOrder(orderInfoVo);
		Map<String, String> data = new HashMap<>(2);
		data.put("orderNo", orderNo);
		return Result.ok(data);
	}


	/**
	 * 分页查询当前用户的订单（我的-订单）
	 *
	 * @param page  页码
	 * @param limit 每页条数
	 * @return 订单分页结果
	 */
	@Operation(summary = "查询当前用户订单分页列表")
	@GuiGuLogin
	@GetMapping("/orderInfo/findUserPage/{page}/{limit}")
	public Result<Page<OrderInfo>> findUserPage(@PathVariable int page, @PathVariable int limit) {
		return Result.ok(orderInfoService.findUserPage(AuthContextHolder.getUserId(), page, limit));
	}


	/**
	 * 根据订单号查询订单
	 * <p>
	 * 服务内部调用（支付模块下单要取金额与内容）和订单详情页共用；
	 * 不校验订单归属，支付模块拿订单号来查时没有用户登录态
	 *
	 * @param orderNo 订单号
	 * @return 订单信息
	 */
	@Operation(summary = "根据订单号查询订单")
	@GetMapping("/orderInfo/getOrderInfo/{orderNo}")
	public Result<OrderInfo> getOrderInfo(@PathVariable String orderNo) {
		return Result.ok(orderInfoService.getOrderInfo(orderNo));
	}

}
