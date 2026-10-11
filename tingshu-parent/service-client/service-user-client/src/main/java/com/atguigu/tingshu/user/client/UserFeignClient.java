package com.atguigu.tingshu.user.client;

import com.atguigu.tingshu.user.client.impl.UserDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * <p>
 * 产品列表API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-user", fallback = UserDegradeFeignClient.class)
public interface UserFeignClient {

    /**
     * 根据用户ID查询用户信息
     * <p>
     * 搜索模块构建索引时要写主播名称、渲染专辑详情时要展示主播昵称与头像，都通过本接口获取
     *
     * @param userId 用户ID
     * @return 用户信息，用户不存在时 data 为 null
     */
    @GetMapping("/api/user/userInfo/getUserInfo/{userId}")
    Result<UserInfoVo> getUserInfo(@PathVariable Long userId);

    /**
     * 判断用户是否为有效VIP会员
     * <p>
     * 专辑模块渲染声音列表时用它决定 VIP 专辑要不要显示付费标识
     *
     * @param userId 用户ID，未登录时传 null
     * @return true-有效VIP
     */
    @GetMapping("/api/user/userInfo/getUserIsVip/{userId}")
    Result<Boolean> getUserIsVip(@PathVariable Long userId);

    /**
     * 查询用户在指定声音中已购买的声音ID集合
     * <p>
     * 专辑模块渲染声音列表时用它决定付费专辑里哪些声音不再显示付费标识
     *
     * @param userId      用户ID，未登录时传 null
     * @param trackIdList 待判断的声音ID集合
     * @return 已购买的声音ID集合
     */
    @PostMapping("/api/user/userPaidTrack/getUserPaidTrackIdList/{userId}")
    Result<List<Long>> getUserPaidTrackIdList(@PathVariable Long userId, @RequestBody List<Long> trackIdList);

    /**
     * 根据ID查询VIP会员服务配置（服务内部调用，订单模块给VIP订单算钱）
     *
     * @param id VIP服务配置ID
     * @return 服务配置，配置不存在时 data 为 null
     */
    @GetMapping("/api/user/vipServiceConfig/getVipServiceConfig/{id}")
    Result<VipServiceConfig> getVipServiceConfig(@PathVariable("id") Long id);

}
