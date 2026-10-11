package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.service.VipServiceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "VIP服务配置管理接口")
@RestController
@RequestMapping("api/user")
@SuppressWarnings({"all"})
public class VipServiceConfigApiController {

	@Autowired
	private VipServiceConfigService vipServiceConfigService;


	/**
	 * 查询全部VIP会员服务配置（开通/续费会员弹窗用）
	 * <p>
	 * 属于公开的营销信息，不强制登录
	 *
	 * @return 会员服务配置列表
	 */
	@Operation(summary = "查询VIP会员服务配置列表")
	@GetMapping("/vipServiceConfig/findAll")
	public Result<List<VipServiceConfig>> findAll() {
		return Result.ok(vipServiceConfigService.list());
	}


	/**
	 * 根据ID查询VIP会员服务配置（服务内部调用，订单模块给VIP订单算钱）
	 * <p>
	 * 不加登录校验：下单算价时没有用户登录态，这里只按ID取价格
	 *
	 * @param id 服务配置ID
	 * @return 会员服务配置，不存在时 data 为 null
	 */
	@Operation(summary = "根据ID查询VIP会员服务配置（服务内部调用）")
	@GetMapping("/vipServiceConfig/getVipServiceConfig/{id}")
	public Result<VipServiceConfig> getVipServiceConfig(@PathVariable Long id) {
		return Result.ok(vipServiceConfigService.getById(id));
	}

}
