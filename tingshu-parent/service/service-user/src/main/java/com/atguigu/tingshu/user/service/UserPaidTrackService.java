package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface UserPaidTrackService extends IService<UserPaidTrack> {

    /**
     * 查询用户在指定声音中已购买的声音ID集合（服务内部调用）
     * <p>
     * 专辑模块渲染声音列表时，用它判断哪些声音已经买过、不需要再显示付费标识。
     * 只查传进来的这批声音，避免一次把用户全部购买记录拉出来
     *
     * @param userId      用户ID，未登录时传 null
     * @param trackIdList 待判断的声音ID集合
     * @return 已购买的声音ID集合；没有购买记录时返回空集合（不返回 null，调用方不用判空）
     */
    List<Long> getUserPaidTrackIdList(Long userId, List<Long> trackIdList);

}
