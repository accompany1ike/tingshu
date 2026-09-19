package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

public interface TrackInfoService extends IService<TrackInfo> {


    /**
     * 保存声音（新增）
     * <p>
     * 一次提交需要落两张表：声音、声音统计，并同步专辑的声音数量，全部在一个事务里完成
     *
     * @param userId      当前登录用户ID（声音归属人）
     * @param trackInfoVo 声音信息
     * @return 新增声音ID
     */
    Long saveTrackInfo(Long userId, TrackInfoVo trackInfoVo);


    /**
     * 查询声音详情（编辑回显用）
     * <p>
     * 只能查询当前登录用户自己的声音
     *
     * @param userId  当前登录用户ID
     * @param trackId 声音ID
     * @return 声音详情
     */
    TrackInfo getTrackInfo(Long userId, Long trackId);


    /**
     * 编辑声音
     * <p>
     * 只更新表单里能改的字段（标题、简介、封面、是否公开，重新上传的音频），
     * 所属专辑、排序值、来源、状态等由服务端维护的字段保持不变
     *
     * @param userId      当前登录用户ID
     * @param trackId     声音ID
     * @param trackInfoVo 声音信息
     */
    void updateTrackInfo(Long userId, Long trackId, TrackInfoVo trackInfoVo);


    /**
     * 删除声音
     * <p>
     * 逻辑删除声音，同时把声音统计一起删掉，并把所属专辑的声音数量减一
     *
     * @param userId  当前登录用户ID
     * @param trackId 声音ID
     */
    void removeTrackInfo(Long userId, Long trackId);


    /**
     * 分页查询当前登录用户的声音列表（我的作品-声音）
     *
     * @param page           页码
     * @param limit          每页条数
     * @param userId         当前登录用户ID
     * @param trackInfoQuery 查询条件（声音状态、标题关键字）
     * @return 分页结果，records 为声音列表
     */
    Page<TrackListVo> findUserTrackPage(int page, int limit, Long userId, TrackInfoQuery trackInfoQuery);

}
