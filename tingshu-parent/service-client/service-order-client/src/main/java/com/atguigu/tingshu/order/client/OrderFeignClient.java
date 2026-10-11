package com.atguigu.tingshu.order.client;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.order.client.impl.OrderDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * <p>
 * 订单模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-order", fallback = OrderDegradeFeignClient.class)
public interface OrderFeignClient {

	/**
	 * 根据订单号查询订单（服务内部调用，支付模块下单时取金额与订单内容）
	 *
	 * @param orderNo 订单号
	 * @return 订单信息
	 */
	@GetMapping("/api/order/orderInfo/getOrderInfo/{orderNo}")
	Result<OrderInfo> getOrderInfo(@PathVariable("orderNo") String orderNo);

}
