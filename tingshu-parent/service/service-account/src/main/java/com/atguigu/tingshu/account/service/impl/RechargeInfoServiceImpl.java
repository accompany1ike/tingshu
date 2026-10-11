package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.RechargeInfoMapper;
import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class RechargeInfoServiceImpl extends ServiceImpl<RechargeInfoMapper, RechargeInfo> implements RechargeInfoService {

	/** 充值单交易标题 */
	private static final String RECHARGE_TITLE = "账户充值";

	@Autowired
	private RechargeInfoMapper rechargeInfoMapper;

	@Autowired
	private UserAccountService userAccountService;

	/**
	 * 提交充值单
	 *
	 * @param userId         当前登录用户ID
	 * @param rechargeInfoVo 充值金额与支付方式
	 * @return 充值记录
	 */
	@Override
	public RechargeInfo submitRecharge(Long userId, RechargeInfoVo rechargeInfoVo) {
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		//金额必须为正：前端「自定义金额」虽然有正则校验，但接口不能信任前端
		BigDecimal amount = rechargeInfoVo == null ? null : rechargeInfoVo.getAmount();
		if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new GuiguException(ResultCodeEnum.ARGUMENT_VALID_ERROR.getCode(), "充值金额必须大于0");
		}
		String payWay = rechargeInfoVo.getPayWay();

		RechargeInfo rechargeInfo = new RechargeInfo();
		rechargeInfo.setUserId(userId);
		rechargeInfo.setOrderNo(this.generateOrderNo());
		rechargeInfo.setRechargeStatus(SystemConstant.RECHARGE_STATUS_UNPAID);
		rechargeInfo.setRechargeAmount(amount.setScale(2, RoundingMode.HALF_UP));
		rechargeInfo.setPayWay(StringUtils.hasText(payWay) ? payWay : SystemConstant.ORDER_PAY_WAY_WEIXIN);
		rechargeInfoMapper.insert(rechargeInfo);
		log.info("提交充值单成功，用户ID：{}，订单号：{}，金额：{}", userId, rechargeInfo.getOrderNo(), rechargeInfo.getRechargeAmount());
		return rechargeInfo;
	}

	/**
	 * 根据充值订单号查询充值记录（服务内部调用）
	 *
	 * @param orderNo 充值订单号
	 * @return 充值记录
	 */
	@Override
	public RechargeInfo getRechargeInfoByOrderNo(String orderNo) {
		if (!StringUtils.hasText(orderNo)) {
			return null;
		}
		return this.getOne(new LambdaQueryWrapper<RechargeInfo>()
				.eq(RechargeInfo::getOrderNo, orderNo), false);
	}

	/**
	 * 处理充值支付成功（MQ 消费入口）
	 *
	 * @param orderNo 充值订单号
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handleRechargePaid(String orderNo) {
		RechargeInfo rechargeInfo = this.getRechargeInfoByOrderNo(orderNo);
		if (rechargeInfo == null) {
			log.error("充值成功但充值记录不存在，订单号：{}", orderNo);
			return;
		}

		//幂等闸门：条件更新带上「原状态=未支付」，微信重复推送时只有一个能改成功
		boolean changed = this.update(new LambdaUpdateWrapper<RechargeInfo>()
				.set(RechargeInfo::getRechargeStatus, SystemConstant.RECHARGE_STATUS_PAID)
				.eq(RechargeInfo::getOrderNo, orderNo)
				.eq(RechargeInfo::getRechargeStatus, SystemConstant.RECHARGE_STATUS_UNPAID));
		if (!changed) {
			log.info("充值记录已是已支付状态，跳过重复到账，订单号：{}", orderNo);
			return;
		}

		//改成功才加钱，保证一笔充值只到账一次
		userAccountService.addBalance(rechargeInfo.getUserId(), rechargeInfo.getRechargeAmount(), orderNo, RECHARGE_TITLE);
		log.info("充值到账处理完成，用户ID：{}，订单号：{}，金额：{}",
				rechargeInfo.getUserId(), orderNo, rechargeInfo.getRechargeAmount());
	}

	/**
	 * 生成充值订单号：RC + 时间戳到毫秒 + 4 位随机数
	 * <p>
	 * 微信支付要求商户订单号在商户号内唯一，加随机数是为了同一毫秒内并发下单不撞号
	 *
	 * @return 充值订单号
	 */
	private String generateOrderNo() {
		String timePart = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
		return "RC" + timePart + ThreadLocalRandom.current().nextInt(1000, 10000);
	}

}
