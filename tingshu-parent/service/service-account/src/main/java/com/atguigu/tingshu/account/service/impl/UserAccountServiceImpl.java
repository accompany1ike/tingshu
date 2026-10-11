package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

	@Autowired
	private UserAccountMapper userAccountMapper;

	@Autowired
	private UserAccountDetailMapper userAccountDetailMapper;

	/**
	 * 初始化用户账户
	 *
	 * @param userId 用户ID
	 */
	@Override
	public void initUserAccount(Long userId) {
		if (userId == null) {
			return;
		}
		//已经开过户就直接返回：注册接口可能被重复调用，而 user_id 上有唯一索引，重复插入会直接抛异常
		if (this.existsAccount(userId)) {
			log.info("用户账户已存在，无需初始化，用户ID：{}", userId);
			return;
		}
		UserAccount userAccount = new UserAccount();
		userAccount.setUserId(userId);
		userAccount.setTotalAmount(BigDecimal.ZERO);
		userAccount.setLockAmount(BigDecimal.ZERO);
		userAccount.setAvailableAmount(BigDecimal.ZERO);
		userAccount.setTotalIncomeAmount(BigDecimal.ZERO);
		userAccount.setTotalPayAmount(BigDecimal.ZERO);
		try {
			userAccountMapper.insert(userAccount);
			log.info("初始化用户账户成功，用户ID：{}", userId);
		} catch (DuplicateKeyException e) {
			//并发下唯一索引兜底：另一个线程已经插好了，这里忽略即可，不能让它影响注册主流程
			log.warn("用户账户已存在，忽略本次初始化，用户ID：{}", userId);
		}
	}

	/**
	 * 查询用户可用余额
	 *
	 * @param userId 用户ID
	 * @return 可用余额
	 */
	@Override
	public BigDecimal getAvailableAmount(Long userId) {
		if (userId == null) {
			return BigDecimal.ZERO;
		}
		UserAccount userAccount = this.getUserAccount(userId);
		BigDecimal availableAmount = userAccount == null ? null : userAccount.getAvailableAmount();
		return availableAmount == null ? BigDecimal.ZERO : availableAmount;
	}

	/**
	 * 账户入账（充值到账时调用）
	 *
	 * @param userId  用户ID
	 * @param amount  入账金额
	 * @param orderNo 关联的充值订单号
	 * @param title   交易标题
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void addBalance(Long userId, BigDecimal amount, String orderNo, String title) {
		if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			log.error("入账参数异常，已忽略，用户ID：{}，金额：{}，订单号：{}", userId, amount, orderNo);
			return;
		}
		//用户可能从没打开过钱包、账户行还没建，先补建再累加
		this.initUserAccount(userId);

		//数据库原子自增：并发充值不会丢金额。金额来自本库的充值记录，不是前端传值，直接拼进 SQL 是安全的
		String amountLiteral = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
		int rows = userAccountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
				.setSql("total_amount = ifnull(total_amount, 0) + " + amountLiteral)
				.setSql("available_amount = ifnull(available_amount, 0) + " + amountLiteral)
				.setSql("total_income_amount = ifnull(total_income_amount, 0) + " + amountLiteral)
				.eq(UserAccount::getUserId, userId));
		if (rows == 0) {
			log.error("入账失败，账户不存在，用户ID：{}，订单号：{}", userId, orderNo);
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "账户不存在，入账失败");
		}

		//补一条交易明细，钱包的「充值记录」靠它
		UserAccountDetail detail = new UserAccountDetail();
		detail.setUserId(userId);
		detail.setTitle(title);
		detail.setTradeType(SystemConstant.ACCOUNT_TRADE_TYPE_DEPOSIT);
		detail.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
		detail.setOrderNo(orderNo);
		userAccountDetailMapper.insert(detail);
		log.info("账户入账成功，用户ID：{}，金额：{}，订单号：{}", userId, amount, orderNo);
	}

	/**
	 * 查询用户账户，查不到就补建一条
	 * <p>
	 * 账户功能上线前注册的老用户库里没有账户行（现有数据里就只有个别用户有），
	 * 直接返回 null 会让钱包页显示异常，所以这里按需补建
	 *
	 * @param userId 用户ID
	 * @return 用户账户，补建失败时返回 null
	 */
	private UserAccount getUserAccount(Long userId) {
		UserAccount userAccount = this.selectOneByUserId(userId);
		if (userAccount == null) {
			this.initUserAccount(userId);
			userAccount = this.selectOneByUserId(userId);
		}
		return userAccount;
	}

	/**
	 * 按用户ID查询账户
	 *
	 * @param userId 用户ID
	 * @return 用户账户
	 */
	private UserAccount selectOneByUserId(Long userId) {
		return userAccountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
				.eq(UserAccount::getUserId, userId)
				.last("limit 1"));
	}

	/**
	 * 判断用户是否已经开过户
	 *
	 * @param userId 用户ID
	 * @return true-已开户
	 */
	private boolean existsAccount(Long userId) {
		Long count = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
				.eq(UserAccount::getUserId, userId));
		return count != null && count > 0;
	}

}
