package com.atguigu.tingshu.order.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.order.OrderDerate;
import com.atguigu.tingshu.model.order.OrderDetail;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.order.helper.SignHelper;
import com.atguigu.tingshu.order.mapper.OrderDerateMapper;
import com.atguigu.tingshu.order.mapper.OrderDetailMapper;
import com.atguigu.tingshu.order.mapper.OrderInfoMapper;
import com.atguigu.tingshu.order.mq.OrderMqSender;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.order.OrderDetailVo;
import com.atguigu.tingshu.vo.order.OrderDerateVo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.atguigu.tingshu.vo.user.UserPayRecordMqVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

	/** 单页最大条数：防止前端传个超大分页把库拖垮 */
	private static final int MAX_PAGE_SIZE = 100;

	/** 折扣按「折」存（8 表示 8 折），换算成实付要用 10 减 */
	private static final BigDecimal FULL_DISCOUNT = BigDecimal.TEN;

	@Autowired
	private OrderInfoMapper orderInfoMapper;

	@Autowired
	private OrderDetailMapper orderDetailMapper;

	@Autowired
	private OrderDerateMapper orderDerateMapper;

	@Autowired
	private AlbumFeignClient albumFeignClient;

	@Autowired
	private UserFeignClient userFeignClient;

	@Autowired
	private OrderMqSender orderMqSender;

	@Autowired
	private RedisTemplate redisTemplate;


	/**
	 * 确认订单：按付款项目算出金额与优惠，返回带签名的待提交订单
	 * <p>
	 * 价格一律服务端现算：专辑按专辑价、声音按专辑单价 × 集数、VIP 按配置的折后价，
	 * 前端只传「买什么」，不传「多少钱」
	 *
	 * @param tradeVo 付款项目（itemType、itemId、trackCount）
	 * @return 待提交的订单信息（含明细、金额、交易号、时间戳、签名）
	 */
	@Override
	public OrderInfoVo trade(TradeVo tradeVo) {
		Long userId = AuthContextHolder.getUserId();
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (tradeVo == null || !StringUtils.hasText(tradeVo.getItemType()) || tradeVo.getItemId() == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "付款项目参数不完整");
		}

		//1.按付款项目类型算钱，同时决定「下单后真正要交付什么」
		//  买声音时如果一次买整张专辑，会归一成专辑订单：交付口径只有「整专辑」和「单集」两种，不会歧义
		String itemType = tradeVo.getItemType();
		Long itemId = tradeVo.getItemId();
		String orderTitle;
		BigDecimal originalAmount;
		BigDecimal derateAmount = BigDecimal.ZERO;
		OrderDetailVo orderDetailVo = new OrderDetailVo();

		if (SystemConstant.ORDER_ITEM_TYPE_ALBUM.equals(itemType)) {
			//1.1 买整张专辑
			AlbumInfo albumInfo = this.getAlbum(itemId);
			orderTitle = albumInfo.getAlbumTitle();
			originalAmount = this.requirePrice(albumInfo.getPrice(), "专辑未定价，无法下单");
			orderDetailVo.setItemId(albumInfo.getId());
			orderDetailVo.setItemName(albumInfo.getAlbumTitle());
			orderDetailVo.setItemUrl(albumInfo.getCoverUrl());
			orderDetailVo.setItemPrice(originalAmount);
			derateAmount = this.calculateAlbumDerate(userId, albumInfo, originalAmount);
		} else if (SystemConstant.ORDER_ITEM_TYPE_TRACK.equals(itemType)) {
			//1.2 买声音：1 集算单集，多集算整专辑
			TrackInfo trackInfo = this.getTrack(itemId);
			AlbumInfo albumInfo = this.getAlbum(trackInfo.getAlbumId());
			int trackCount = tradeVo.getTrackCount() == null ? 1 : Math.max(1, tradeVo.getTrackCount());
			BigDecimal unitPrice = this.requirePrice(albumInfo.getPrice(), "专辑未定价，无法下单");

			if (trackCount > 1) {
				//整专辑购买：归一成专辑订单，交付的是整张专辑
				itemType = SystemConstant.ORDER_ITEM_TYPE_ALBUM;
				itemId = albumInfo.getId();
				orderTitle = albumInfo.getAlbumTitle();
				originalAmount = unitPrice.multiply(BigDecimal.valueOf(trackCount));
				orderDetailVo.setItemId(albumInfo.getId());
				orderDetailVo.setItemName(albumInfo.getAlbumTitle());
				orderDetailVo.setItemUrl(albumInfo.getCoverUrl());
			} else {
				orderTitle = trackInfo.getTrackTitle();
				originalAmount = unitPrice;
				orderDetailVo.setItemId(trackInfo.getId());
				orderDetailVo.setItemName(trackInfo.getTrackTitle());
				orderDetailVo.setItemUrl(StringUtils.hasText(trackInfo.getCoverUrl())
						? trackInfo.getCoverUrl() : albumInfo.getCoverUrl());
			}
			orderDetailVo.setItemPrice(originalAmount);
			derateAmount = this.calculateAlbumDerate(userId, albumInfo, originalAmount);
		} else if (SystemConstant.ORDER_ITEM_TYPE_VIP.equals(itemType)) {
			//1.3 开会员：原价与折后价都来自配置，差价就是优惠
			VipServiceConfig vipServiceConfig = this.getData(userFeignClient.getVipServiceConfig(itemId));
			if (vipServiceConfig == null) {
				throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "会员服务配置不存在");
			}
			orderTitle = vipServiceConfig.getName();
			BigDecimal vipPrice = this.requirePrice(vipServiceConfig.getPrice(), "会员服务未定价");
			BigDecimal vipDiscountPrice = vipServiceConfig.getDiscountPrice() == null
					? vipPrice : vipServiceConfig.getDiscountPrice();
			originalAmount = vipPrice;
			derateAmount = vipPrice.subtract(vipDiscountPrice).max(BigDecimal.ZERO);
			orderDetailVo.setItemId(vipServiceConfig.getId());
			orderDetailVo.setItemName(vipServiceConfig.getName());
			orderDetailVo.setItemUrl(vipServiceConfig.getImageUrl());
			orderDetailVo.setItemPrice(vipDiscountPrice);
		} else {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "不支持的付款项目类型");
		}

		//2.算总价：金额统一保留两位小数，避免出现 9.999999 这种数
		originalAmount = this.scale2(originalAmount);
		derateAmount = this.scale2(derateAmount).min(originalAmount);
		BigDecimal orderAmount = this.scale2(originalAmount.subtract(derateAmount));
		if (orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "订单金额异常，请联系客服");
		}

		//3.组装待提交订单：交易号 + 时间戳 + 签名，提交时要拿签名回来验
		OrderInfoVo orderInfoVo = new OrderInfoVo();
		orderInfoVo.setItemType(itemType);
		orderInfoVo.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
		orderInfoVo.setOriginalAmount(originalAmount);
		orderInfoVo.setDerateAmount(derateAmount);
		orderInfoVo.setOrderAmount(orderAmount);
		orderInfoVo.setOrderDetailVoList(Collections.singletonList(orderDetailVo));
		orderInfoVo.setOrderDerateVoList(this.buildDerateVoList(orderTitle, derateAmount));
		orderInfoVo.setTradeNo(UUID.randomUUID().toString().replace("-", ""));
		orderInfoVo.setTimestamp(SignHelper.getTimestamp());
		orderInfoVo.setSign(SignHelper.getSign(this.buildSignMap(orderInfoVo)));
		log.info("确认订单完成，用户ID：{}，项目类型：{}，项目ID：{}，应付：{}",
				userId, itemType, itemId, orderAmount);
		return orderInfoVo;
	}


	/**
	 * 提交订单：防重复提交、验签、落库、发超时取消的延迟消息
	 * <p>
	 * 防重靠 Redis 的 SET NX：把 tradeNo 当一次性凭证抢，抢到才允许落库。
	 * 抢不到说明是重复提交（前端手抖双击、网络重试、用户狂点），直接拒掉，一条订单都不会多出来
	 *
	 * @param orderInfoVo 确认订单返回的对象（原样带回）
	 * @return 订单号
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public String submitOrder(OrderInfoVo orderInfoVo) {
		Long userId = AuthContextHolder.getUserId();
		if (userId == null) {
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
		}
		if (orderInfoVo == null || CollectionUtils.isEmpty(orderInfoVo.getOrderDetailVoList())) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "订单信息不完整");
		}

		//1.防重：tradeNo 由 trade() 生成、又被算进签名，前端必须原样带回，所以不可伪造；
		//  「确认订单」页每次加载都会重新 trade() 拿新 tradeNo，因此同一个 tradeNo 只可能来自同一次下单意图
		String tradeNo = orderInfoVo.getTradeNo();
		if (!StringUtils.hasText(tradeNo)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "订单信息不完整");
		}
		String tradeKey = RedisConstant.ORDER_TRADE_NO_PREFIX + tradeNo;
		Boolean tradeFirst = redisTemplate.opsForValue()
				.setIfAbsent(tradeKey, userId, RedisConstant.ORDER_TRADE_EXPIRE, TimeUnit.SECONDS);
		if (!Boolean.TRUE.equals(tradeFirst)) {
			throw new GuiguException(ResultCodeEnum.ORDER_SUBMIT_REPEAT.getCode(), "订单已提交，请勿重复操作");
		}

		try {
			//2.验签：签名里带了金额与时间戳，改过金额或者拖太久不提交都会被拒
			Map<String, Object> signMap = this.buildSignMap(orderInfoVo);
			signMap.put("sign", orderInfoVo.getSign());
			SignHelper.checkSign(signMap);

			//3.落订单主表
			String orderNo = this.generateOrderNo();
			OrderInfo orderInfo = new OrderInfo();
			orderInfo.setUserId(userId);
			orderInfo.setOrderNo(orderNo);
			orderInfo.setOrderTitle(orderInfoVo.getOrderDetailVoList().get(0).getItemName());
			orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_UNPAID);
			orderInfo.setItemType(orderInfoVo.getItemType());
			orderInfo.setPayWay(orderInfoVo.getPayWay());
			orderInfo.setOriginalAmount(this.scale2(orderInfoVo.getOriginalAmount()));
			orderInfo.setDerateAmount(this.scale2(orderInfoVo.getDerateAmount()));
			orderInfo.setOrderAmount(this.scale2(orderInfoVo.getOrderAmount()));
			orderInfoMapper.insert(orderInfo);

			//4.落订单明细
			for (OrderDetailVo detailVo : orderInfoVo.getOrderDetailVoList()) {
				OrderDetail orderDetail = new OrderDetail();
				orderDetail.setOrderId(orderInfo.getId());
				orderDetail.setItemId(detailVo.getItemId());
				orderDetail.setItemName(detailVo.getItemName());
				orderDetail.setItemUrl(detailVo.getItemUrl());
				orderDetail.setItemPrice(this.scale2(detailVo.getItemPrice()));
				orderDetailMapper.insert(orderDetail);
			}

			//5.落减免明细：没有优惠就不写，前端按「没有减免行」处理
			if (!CollectionUtils.isEmpty(orderInfoVo.getOrderDerateVoList())) {
				for (OrderDerateVo derateVo : orderInfoVo.getOrderDerateVoList()) {
					OrderDerate orderDerate = new OrderDerate();
					orderDerate.setOrderId(orderInfo.getId());
					orderDerate.setDerateType(derateVo.getDerateType());
					orderDerate.setDerateAmount(this.scale2(derateVo.getDerateAmount()));
					orderDerate.setRemarks(derateVo.getRemarks());
					orderDerateMapper.insert(orderDerate);
				}
			}

			//6.发延迟消息：超时未支付自动取消，避免一直占着「未支付」状态
			orderMqSender.sendCancelOrderDelay(orderNo);
			log.info("提交订单成功，用户ID：{}，订单号：{}，应付：{}", userId, orderNo, orderInfo.getOrderAmount());
			return orderNo;
		} catch (RuntimeException e) {
			//失败必须把标记放掉：否则用户在标记有效期内连正常的重试都会被自己这次失败请求挡住
			redisTemplate.delete(tradeKey);
			log.warn("提交订单失败，已释放防重标记，用户ID：{}，交易号：{}", userId, tradeNo, e);
			throw e;
		}
	}


	/**
	 * 分页查询当前用户的订单（我的-订单）
	 *
	 * @param userId 当前登录用户ID
	 * @param page   页码
	 * @param limit  每页条数
	 * @return 订单分页结果
	 */
	@Override
	public Page<OrderInfo> findUserPage(Long userId, int page, int limit) {
		int pageNo = Math.max(page, 1);
		int pageSize = limit < 1 ? 10 : Math.min(limit, MAX_PAGE_SIZE);
		Page<OrderInfo> pageResult = new Page<>(pageNo, pageSize);
		if (userId == null) {
			return pageResult;
		}
		return this.page(pageResult, new LambdaQueryWrapper<OrderInfo>()
				.eq(OrderInfo::getUserId, userId)
				.orderByDesc(OrderInfo::getId));
	}


	/**
	 * 根据订单号查询订单（订单详情 / 支付模块内部调用）
	 *
	 * @param orderNo 订单号
	 * @return 订单信息，不存在时返回 null
	 */
	@Override
	public OrderInfo getOrderInfo(String orderNo) {
		if (!StringUtils.hasText(orderNo)) {
			return null;
		}
		return this.getOne(new LambdaQueryWrapper<OrderInfo>()
				.eq(OrderInfo::getOrderNo, orderNo), false);
	}


	/**
	 * 订单支付成功后的处理：改状态 + 通知用户模块发货 + 专辑购买量加一
	 * <p>
	 * 微信回调可能重复推送，所以这里靠「条件更新」保证幂等：
	 * 只有把状态从未支付改成已支付的那一次才会往下走，重复消息不会重复发货、重复加购买量
	 * <p>
	 * 刻意不加 @Transactional：这里只有一次状态更新，原子性由条件更新本身保证；
	 * 不包事务才能让后续的 MQ 消息严格发生在状态落库之后，避免事务回滚时消息已经发出去的假成功
	 *
	 * @param orderNo 订单号
	 */
	@Override
	public void updateOrderPaid(String orderNo) {
		OrderInfo orderInfo = this.getOrderInfo(orderNo);
		if (orderInfo == null) {
			log.error("支付成功但订单不存在，订单号：{}", orderNo);
			return;
		}
		if (!SystemConstant.ORDER_STATUS_UNPAID.equals(orderInfo.getOrderStatus())) {
			log.info("订单不是未支付状态，跳过支付成功处理，订单号：{}，当前状态：{}",
					orderNo, orderInfo.getOrderStatus());
			return;
		}

		//1.条件更新：带上「原状态=未支付」这个条件，并发/重复消息只有一个能改成功
		boolean updated = this.update(new LambdaUpdateWrapper<OrderInfo>()
				.set(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_PAID)
				.eq(OrderInfo::getId, orderInfo.getId())
				.eq(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_UNPAID));
		if (!updated) {
			log.info("订单已被其它请求处理为已支付，跳过，订单号：{}", orderNo);
			return;
		}

		//2.通知用户模块发货：写已购专辑/已购声音、开通会员
		UserPayRecordMqVo payRecordMqVo = new UserPayRecordMqVo();
		payRecordMqVo.setOrderNo(orderNo);
		payRecordMqVo.setUserId(orderInfo.getUserId());
		payRecordMqVo.setItemType(orderInfo.getItemType());
		payRecordMqVo.setItemId(this.findOrderItemId(orderInfo.getId()));
		orderMqSender.sendUserPayRecord(payRecordMqVo);

		//3.专辑购买量 +1：排行榜的「购买量」维度就靠这一条
		orderMqSender.sendAlbumBuyStat(this.resolveAlbumId(orderInfo, payRecordMqVo.getItemId()));
		log.info("订单支付成功处理完成，订单号：{}，用户ID：{}", orderNo, orderInfo.getUserId());
	}


	/**
	 * 取消订单（超时未支付）
	 *
	 * @param orderNo 订单号
	 */
	@Override
	public void cancelOrder(String orderNo) {
		OrderInfo orderInfo = this.getOrderInfo(orderNo);
		if (orderInfo == null) {
			return;
		}
		//只取消未支付的：已支付的订单不能被延迟消息改成已取消
		boolean updated = this.update(new LambdaUpdateWrapper<OrderInfo>()
				.set(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_CANCEL)
				.eq(OrderInfo::getId, orderInfo.getId())
				.eq(OrderInfo::getOrderStatus, SystemConstant.ORDER_STATUS_UNPAID));
		if (updated) {
			log.info("订单超时未支付，已取消，订单号：{}", orderNo);
		} else {
			log.info("订单不是未支付状态，无需取消，订单号：{}，当前状态：{}",
					orderNo, orderInfo.getOrderStatus());
		}
	}


	/**
	 * 取订单第一个明细的项目ID（一个订单目前只有一个付款项目）
	 *
	 * @param orderId 订单主键
	 * @return 项目ID，明细缺失时返回 null
	 */
	private Long findOrderItemId(Long orderId) {
		List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>()
				.eq(OrderDetail::getOrderId, orderId)
				.orderByAsc(OrderDetail::getId));
		if (CollectionUtils.isEmpty(orderDetailList)) {
			return null;
		}
		return orderDetailList.get(0).getItemId();
	}


	/**
	 * 解析订单对应的专辑ID：购买量统计挂在专辑维度上
	 *
	 * @param orderInfo 订单
	 * @param itemId    订单项目ID（专辑ID 或 声音ID）
	 * @return 专辑ID；会员订单不涉及专辑，返回 null
	 */
	private Long resolveAlbumId(OrderInfo orderInfo, Long itemId) {
		if (itemId == null) {
			return null;
		}
		if (SystemConstant.ORDER_ITEM_TYPE_ALBUM.equals(orderInfo.getItemType())) {
			return itemId;
		}
		if (SystemConstant.ORDER_ITEM_TYPE_TRACK.equals(orderInfo.getItemType())) {
			TrackInfo trackInfo = this.getData(albumFeignClient.findTrackInfoByIds(List.of(itemId)))
					.stream().findFirst().orElse(null);
			return trackInfo == null ? null : trackInfo.getAlbumId();
		}
		//会员订单：购买量统计不挂在专辑上
		return null;
	}


	/**
	 * 计算专辑类订单的优惠金额
	 * <p>
	 * 会员优先用「会员专享折扣」，否则用普通折扣；折扣字段存的是「几折」，
	 * 比如 8 表示 8 折，实付就是原价 × 0.8
	 *
	 * @param userId         用户ID
	 * @param albumInfo      专辑
	 * @param originalAmount 原价
	 * @return 减免金额（不会超过原价）
	 */
	private BigDecimal calculateAlbumDerate(Long userId, AlbumInfo albumInfo, BigDecimal originalAmount) {
		BigDecimal discount = albumInfo.getDiscount();
		Boolean isVip = this.getData(userFeignClient.getUserIsVip(userId));
		if (Boolean.TRUE.equals(isVip) && albumInfo.getVipDiscount() != null
				&& albumInfo.getVipDiscount().compareTo(BigDecimal.ZERO) > 0) {
			discount = albumInfo.getVipDiscount();
		}
		if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0
				|| discount.compareTo(FULL_DISCOUNT) >= 0) {
			return BigDecimal.ZERO;
		}
		//减免 = 原价 × (10 - 折扣) / 10
		BigDecimal derateAmount = originalAmount
				.multiply(FULL_DISCOUNT.subtract(discount))
				.divide(FULL_DISCOUNT, 2, RoundingMode.HALF_UP);
		return derateAmount.min(originalAmount);
	}


	/**
	 * 组装减免明细：只有真的有优惠时才给一行，否则返回空列表
	 *
	 * @param orderTitle 订单标题
	 * @param derateAmount 减免金额
	 * @return 减免明细列表
	 */
	private List<OrderDerateVo> buildDerateVoList(String orderTitle, BigDecimal derateAmount) {
		if (derateAmount == null || derateAmount.compareTo(BigDecimal.ZERO) <= 0) {
			return Collections.emptyList();
		}
		OrderDerateVo derateVo = new OrderDerateVo();
		derateVo.setDerateType(SystemConstant.ORDER_DERATE_ALBUM_DISCOUNT);
		derateVo.setDerateAmount(derateAmount);
		derateVo.setRemarks(orderTitle + " 折扣优惠");
		List<OrderDerateVo> derateVoList = new ArrayList<>(1);
		derateVoList.add(derateVo);
		return derateVoList;
	}


	/**
	 * 构造参与签名的字段集合
	 * <p>
	 * 金额统一按两位小数的字符串参与签名：前端把 JSON 数字取出来后，
	 * 8.00 会变成 8，直接对 BigDecimal 做 toString 两边对不上，会导致验签失败
	 *
	 * @param orderInfoVo 待提交订单
	 * @return 有序的待签名参数
	 */
	private Map<String, Object> buildSignMap(OrderInfoVo orderInfoVo) {
		Map<String, Object> signMap = new TreeMap<>();
		signMap.put("tradeNo", orderInfoVo.getTradeNo());
		signMap.put("timestamp", orderInfoVo.getTimestamp());
		signMap.put("itemType", orderInfoVo.getItemType());
		signMap.put("originalAmount", this.scale2Text(orderInfoVo.getOriginalAmount()));
		signMap.put("derateAmount", this.scale2Text(orderInfoVo.getDerateAmount()));
		signMap.put("orderAmount", this.scale2Text(orderInfoVo.getOrderAmount()));
		return signMap;
	}


	/**
	 * 生成订单号：TS + 时间戳到毫秒 + 4 位随机数
	 * <p>
	 * 微信支付要求商户订单号在商户号内唯一，加随机数是为了同一毫秒内并发下单不撞号
	 *
	 * @return 订单号
	 */
	private String generateOrderNo() {
		String timePart = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
		return "TS" + timePart + ThreadLocalRandom.current().nextInt(1000, 10000);
	}


	private AlbumInfo getAlbum(Long albumId) {
		AlbumInfo albumInfo = this.getData(albumFeignClient.getAlbumInfoById(albumId));
		if (albumInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "专辑不存在");
		}
		return albumInfo;
	}


	private TrackInfo getTrack(Long trackId) {
		TrackInfo trackInfo = this.getData(albumFeignClient.findTrackInfoByIds(List.of(trackId)))
				.stream().findFirst().orElse(null);
		if (trackInfo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "声音不存在");
		}
		return trackInfo;
	}


	private BigDecimal requirePrice(BigDecimal price, String message) {
		if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), message);
		}
		return price;
	}


	private BigDecimal scale2(BigDecimal amount) {
		return (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
	}


	private String scale2Text(BigDecimal amount) {
		return this.scale2(amount).toPlainString();
	}


	/**
	 * 远程调用结果统一取值：服务降级、业务异常时 data 都是 null，调用方按「没查到」处理
	 *
	 * @param result 远程调用结果
	 * @return 业务数据
	 */
	private <T> T getData(Result<T> result) {
		return result == null ? null : result.getData();
	}

}
