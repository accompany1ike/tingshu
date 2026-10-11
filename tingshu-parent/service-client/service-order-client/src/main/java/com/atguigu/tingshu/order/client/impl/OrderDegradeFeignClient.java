package com.atguigu.tingshu.order.client.impl;


import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.client.OrderFeignClient;
import org.springframework.stereotype.Component;

@Component
public class OrderDegradeFeignClient implements OrderFeignClient {

	/**
	 * 订单服务不可用时返回空，支付模块按「订单查不到」处理，不会拿到半个订单继续下单
	 *
	 * @param orderNo 订单号
	 * @return 恒为 data 为 null 的成功响应
	 */
	@Override
	public Result<OrderInfo> getOrderInfo(String orderNo) {
		return Result.ok(null);
	}

}
