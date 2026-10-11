package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface RechargeInfoService extends IService<RechargeInfo> {

	/**
	 * 提交充值单
	 * <p>
	 * 只落一条「未支付」的充值记录并返回充值订单号，钱由微信支付回调之后才真正到账。
	 * 金额以服务端校验为准，不信任前端传值
	 *
	 * @param userId         当前登录用户ID
	 * @param rechargeInfoVo 充值金额与支付方式
	 * @return 充值记录（含充值订单号）
	 */
	RechargeInfo submitRecharge(Long userId, RechargeInfoVo rechargeInfoVo);

	/**
	 * 根据充值订单号查询充值记录（服务内部调用）
	 * <p>
	 * 支付模块发起微信下单时要拿充值金额，金额一律以账户库为准，不能让前端传
	 *
	 * @param orderNo 充值订单号
	 * @return 充值记录，不存在时返回 null
	 */
	RechargeInfo getRechargeInfoByOrderNo(String orderNo);

	/**
	 * 处理充值支付成功（MQ 消费入口）
	 * <p>
	 * 先幂等把充值记录从「未支付」改成「已支付」，只有改成功的那一次才给账户加钱，
	 * 微信重复推送不会重复到账
	 *
	 * @param orderNo 充值订单号
	 */
	void handleRechargePaid(String orderNo);

}
