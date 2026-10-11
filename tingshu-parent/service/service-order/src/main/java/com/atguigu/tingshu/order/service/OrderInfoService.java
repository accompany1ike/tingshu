package com.atguigu.tingshu.order.service;

import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface OrderInfoService extends IService<OrderInfo> {

	/**
	 * 确认订单：按付款项目算出金额与优惠，返回带签名的待提交订单
	 * <p>
	 * 前端「确认订单」页只传 itemType + itemId（买声音时再带 trackCount），
	 * 价格一律由服务端现算，避免前端改价下单；算完顺便生成交易号与签名，
	 * 提交订单时要拿签名回来验，防止中途被篡改
	 *
	 * @param tradeVo 付款项目（itemType、itemId、trackCount）
	 * @return 待提交的订单信息（含明细、金额、交易号、时间戳、签名）
	 */
	OrderInfoVo trade(TradeVo tradeVo);


	/**
	 * 提交订单：验签、落库、发超时取消的延迟消息
	 * <p>
	 * 只落库不支付，支付由支付模块发起；订单状态为「未支付」，
	 * 超过 MqConst.CANCEL_ORDER_DELAY_TIME 秒未支付会被延迟消息自动取消
	 *
	 * @param orderInfoVo 确认订单返回的对象（原样带回）
	 * @return 订单号
	 */
	String submitOrder(OrderInfoVo orderInfoVo);


	/**
	 * 分页查询当前用户的订单（我的-订单）
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码，从 1 开始
	 * @param limit  每页条数
	 * @return 订单分页结果
	 */
	Page<OrderInfo> findUserPage(Long userId, int page, int limit);


	/**
	 * 根据订单号查询订单（订单详情 / 支付模块内部调用）
	 *
	 * @param orderNo 订单号
	 * @return 订单信息，不存在时返回 null
	 */
	OrderInfo getOrderInfo(String orderNo);


	/**
	 * 订单支付成功后的处理：改状态 + 通知用户模块发货 + 专辑购买量加一
	 * <p>
	 * 支付回调可能重复推送，所以这里必须幂等：只有「未支付 -> 已支付」这一次会真正生效
	 *
	 * @param orderNo 订单号
	 */
	void updateOrderPaid(String orderNo);


	/**
	 * 取消订单（超时未支付）
	 * <p>
	 * 只有「未支付」的订单会被取消，已支付的订单忽略，
	 * 避免用户刚付完钱、延迟消息紧接着把订单改成已取消
	 *
	 * @param orderNo 订单号
	 */
	void cancelOrder(String orderNo);

}
