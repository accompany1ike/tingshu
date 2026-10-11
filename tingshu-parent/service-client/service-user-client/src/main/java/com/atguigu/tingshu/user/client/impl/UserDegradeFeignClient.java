package com.atguigu.tingshu.user.client.impl;


import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 用户模块远程调用失败时的兜底实现（服务降级）
 */
@Slf4j
@Component
public class UserDegradeFeignClient implements UserFeignClient {

    @Override
    public Result<UserInfoVo> getUserInfo(Long userId) {
        log.error("查询用户信息失败，服务降级，用户ID：{}", userId);
        return Result.ok(null);
    }

    /**
     * 降级时一律按"非VIP"处理
     * <p>
     * 宁可让VIP用户多看到一次付费标识，也不能因为查不到会员状态就把付费内容当免费放出去
     *
     * @param userId 用户ID
     * @return 恒为 false
     */
    @Override
    public Result<Boolean> getUserIsVip(Long userId) {
        log.error("查询用户VIP状态失败，服务降级，按非VIP处理，用户ID：{}", userId);
        return Result.ok(false);
    }

    /**
     * 降级时返回空集合
     * <p>
     * 含义是"这些声音一个都没买过"，同样偏向保守：付费专辑的声音都会显示付费标识
     *
     * @param userId      用户ID
     * @param trackIdList 待判断的声音ID集合
     * @return 空集合
     */
    @Override
    public Result<List<Long>> getUserPaidTrackIdList(Long userId, List<Long> trackIdList) {
        log.error("查询用户已购声音失败，服务降级，按未购买处理，用户ID：{}，声音数量：{}",
                userId, trackIdList == null ? 0 : trackIdList.size());
        return Result.ok(Collections.emptyList());
    }

    /**
     * 降级时返回 null
     * <p>
     * 订单模块拿到 null 会按「配置不存在」拒绝下单，不会用错误的价格生成订单
     *
     * @param id VIP服务配置ID
     * @return data 为 null 的成功响应
     */
    @Override
    public Result<VipServiceConfig> getVipServiceConfig(Long id) {
        log.error("查询VIP服务配置失败，服务降级，配置ID：{}", id);
        return Result.ok(null);
    }

}
