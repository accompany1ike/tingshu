package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserPayRecordMqVo;

public interface UserPaidService {

	/**
	 * 订单支付成功后的「发货」：写已购记录、开通会员
	 * <p>
	 * 由支付成功消息驱动，同一个订单号重复投递只会生效一次，
	 * 保证用户不会因为微信重复回调而拿到两份权益
	 *
	 * @param userPayRecordMqVo 支付成功消息（订单号、用户ID、项目类型、项目ID）
	 */
	void savePaidRecord(UserPayRecordMqVo userPayRecordMqVo);

}
