package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.model.account.UserAccount;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;

public interface UserAccountService extends IService<UserAccount> {

	/**
	 * 初始化用户账户
	 * <p>
	 * 用户注册成功后调用，账户各项金额从 0 开始。接口幂等：已经开过户的用户再调用不会重复建账户，
	 * 也不会把已有余额覆盖成 0
	 *
	 * @param userId 用户ID
	 */
	void initUserAccount(Long userId);

	/**
	 * 查询用户可用余额
	 * <p>
	 * 账户功能上线前注册的老用户库里没有账户行，这里会补建一条再返回 0，
	 * 保证"我的-钱包"不会因为查不到账户而报错
	 *
	 * @param userId 用户ID
	 * @return 可用余额，账户不存在或金额为空时返回 0
	 */
	BigDecimal getAvailableAmount(Long userId);

	/**
	 * 账户入账（充值到账时调用）
	 * <p>
	 * 累加必须走数据库原子自增，不能「先查再 set 回写」，并发下会丢金额；
	 * 同时补一条交易明细，钱包的「充值记录」靠它
	 *
	 * @param userId  用户ID
	 * @param amount  入账金额，必须大于 0
	 * @param orderNo 关联的充值订单号
	 * @param title   交易标题
	 */
	void addBalance(Long userId, BigDecimal amount, String orderNo, String title);

}
