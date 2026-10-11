package com.atguigu.tingshu.account;

import com.atguigu.tingshu.account.impl.AccountDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.RechargeInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * <p>
 * 账号模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-account", fallback = AccountDegradeFeignClient.class)
public interface AccountFeignClient {

	/**
	 * 初始化用户账户（服务内部调用）
	 * <p>
	 * 用户注册成功后由 user 模块调用，账户余额从 0 开始；接口幂等，重复调用不会重复开户
	 *
	 * @param userId 用户ID
	 * @return 统一返回结果
	 */
	@PostMapping("/api/account/userAccount/initUserAccount/{userId}")
	Result initUserAccount(@PathVariable Long userId);

	/**
	 * 根据充值订单号查询充值记录（服务内部调用）
	 * <p>
	 * 支付模块发起微信下单时要拿充值金额，金额一律以账户库为准，不能让前端传
	 *
	 * @param orderNo 充值订单号
	 * @return 充值记录，不存在时 data 为 null
	 */
	@GetMapping("/api/account/rechargeInfo/getRechargeInfo/{orderNo}")
	Result<RechargeInfo> getRechargeInfo(@PathVariable String orderNo);

}
