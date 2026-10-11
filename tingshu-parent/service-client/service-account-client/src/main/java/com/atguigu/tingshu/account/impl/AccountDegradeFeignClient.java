package com.atguigu.tingshu.account.impl;


import com.atguigu.tingshu.account.AccountFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.account.RechargeInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AccountDegradeFeignClient implements AccountFeignClient {

	/**
	 * 账户服务不可用时降级
	 * <p>
	 * 只记日志不抛异常：注册流程不能因为账户模块挂了就走不下去，
	 * 账户行会在用户首次查余额时按需补建
	 *
	 * @param userId 用户ID
	 * @return 统一返回结果
	 */
	@Override
	public Result initUserAccount(Long userId) {
		log.error("初始化用户账户失败，账户服务降级，用户ID：{}", userId);
		return Result.ok();
	}

	/**
	 * 账户服务不可用时降级
	 * <p>
	 * 返回 null 而不是抛异常：调用方按「充值单不存在」处理，直接给出可读提示，
	 * 避免账户模块故障时支付模块跟着 500
	 *
	 * @param orderNo 充值订单号
	 * @return 统一返回结果，data 为 null
	 */
	@Override
	public Result<RechargeInfo> getRechargeInfo(String orderNo) {
		log.error("查询充值记录失败，账户服务降级，充值订单号：{}", orderNo);
		return Result.ok(null);
	}

}
