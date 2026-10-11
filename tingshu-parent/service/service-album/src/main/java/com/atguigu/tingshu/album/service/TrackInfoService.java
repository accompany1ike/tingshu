package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackPaidSettingVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.album.TrackStatVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

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


    /**
     * 根据专辑ID分页查询声音列表（专辑详情页用）
     * <p>
     * 每条声音都要判断是否显示付费标识（isShowPaidMark），判断依据是专辑的付费类型
     * 加上"当前用户是否已获得"：免费专辑不标记；VIP免费专辑看用户是不是有效VIP；
     * 付费专辑看用户有没有买过这条声音；另外专辑允许免费试听的前 N 集一律不标记
     *
     * @param albumId 专辑ID
     * @param page    页码
     * @param limit   每页条数
     * @param userId  当前登录用户ID，未登录传 null（未登录时VIP与购买记录都按"没有"处理）
     * @return 分页结果，records 为声音列表（含付费标识）
     */
    Page<AlbumTrackListVo> findAlbumTrackPage(Long albumId, int page, int limit, Long userId);


    /**
     * 更新声音统计信息（MQ 消费者调用）
     * <p>
     * 按 statType 对 track_stat 里对应的那一行做增量累加：播放量、收藏量、点赞量、
     * 评论数的变更都走这一个入口。统计行不存在时补插一条，避免老数据丢计数
     *
     * @param trackStatMqVo 统计变更消息（专辑ID、声音ID、统计类型、增量）
     */
    void updateTrackStat(TrackStatMqVo trackStatMqVo);


    /**
     * 查询声音统计信息（播放页用）
     * <p>
     * 把 track_stat 里的播放量、收藏量、点赞量、评论数四行汇总成一个对象，缺失的统计项按 0 处理
     *
     * @param trackId 声音ID
     * @return 声音统计信息
     */
    TrackStatVo getTrackStatVo(Long trackId);


    /**
     * 根据声音ID集合批量查询声音（服务内部调用）
     * <p>
     * 「我的-收藏 / 历史」列表页一次要展示几十条声音，逐条查会打出一串请求；
     * 不做归属校验，列表页展示的是用户自己收藏/听过的声音
     *
     * @param trackIdList 声音ID集合
     * @return 声音集合，入参为空时返回空集合
     */
    List<TrackInfo> findTrackInfoByIds(List<Long> trackIdList);


    /**
     * 查询某个声音的购买选项（详情页「购买本集」弹窗用）
     * <p>
     * 弹窗里要同时给出「单集购买」和「整专辑购买」两种买法的价格，
     * 价格一律按专辑上的单价现算，保证和下单时服务端算出来的金额一致
     *
     * @param trackId 声音ID
     * @return 购买选项列表，专辑未定价时返回空列表
     */
    List<TrackPaidSettingVo> findUserTrackPaidList(Long trackId);

}
