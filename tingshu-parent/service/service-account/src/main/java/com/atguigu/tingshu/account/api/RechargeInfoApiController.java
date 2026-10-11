package com.atguigu.tingshu.account.api;

import com.atguigu.tingshu.account.service.RechargeInfoService;
import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.account.RechargeInfo;
import com.atguigu.tingshu.vo.account.RechargeInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "充值管理")
@RestController
@RequestMapping("api/account")
@SuppressWarnings({"all"})
public class RechargeInfoApiController {

	@Autowired
	private RechargeInfoService rechargeInfoService;

	/**
	 * 提交充值单
	 * <p>
	 * 前端拿到返回的 orderNo 后，接着调微信下单接口（支付类型 1302）唤起支付；
	 * 这一步只落一条「未支付」的充值记录，钱要等微信回调才真正到账
	 *
	 * @param rechargeInfoVo 充值金额与支付方式
	 * @return 充值记录（前端只取 orderNo）
	 */
	@Operation(summary = "提交充值单")
	@GuiGuLogin
	@PostMapping("/rechargeInfo/submitRecharge")
	public Result<RechargeInfo> submitRecharge(@RequestBody RechargeInfoVo rechargeInfoVo) {
		return Result.ok(rechargeInfoService.submitRecharge(AuthContextHolder.getUserId(), rechargeInfoVo));
	}

	/**
	 * 根据充值订单号查询充值记录（服务内部调用）
	 * <p>
	 * 支付模块发起微信下单时要拿充值金额，金额一律以账户库为准
	 *
	 * @param orderNo 充值订单号
	 * @return 充值记录，不存在时 data 为 null
	 */
	@Operation(summary = "查询充值记录")
	@GetMapping("/rechargeInfo/getRechargeInfo/{orderNo}")
	public Result<RechargeInfo> getRechargeInfo(@PathVariable String orderNo) {
		return Result.ok(rechargeInfoService.getRechargeInfoByOrderNo(orderNo));
	}

}
