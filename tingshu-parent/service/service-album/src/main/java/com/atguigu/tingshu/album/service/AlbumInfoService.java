package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AlbumInfoService extends IService<AlbumInfo> {


    /**
     * 保存专辑（新增）
     *
     * @param userId      当前登录用户ID（专辑归属人）
     * @param albumInfoVo 专辑信息
     * @return 新增专辑ID
     */
    Long saveAlbumInfo(Long userId, AlbumInfoVo albumInfoVo);


    /**
     * 分页查询当前登录用户的专辑列表（最新添加的排在最前面）
     *
     * @param page          页码
     * @param limit         每页条数
     * @param userId        当前登录用户ID
     * @param albumInfoQuery 查询条件（专辑状态、标题关键字）
     * @return 分页结果，records 为专辑列表
     */
    Page<AlbumListVo> findUserAlbumPage(int page, int limit, Long userId, AlbumInfoQuery albumInfoQuery);


    /**
     * 查询当前登录用户的全部专辑（下拉选择用）
     * <p>
     * 新增声音时要选所属专辑，只返回 id 与标题，不做分页
     *
     * @param userId 当前登录用户ID
     * @return 专辑集合
     */
    List<AlbumInfo> findUserAllAlbumList(Long userId);


    /**
     * 查询专辑详情（编辑回显用）
     * <p>
     * 只能查询当前登录用户自己的专辑，返回的 albumAttributeValueVoList
     * 已回填标签名与标签值名，前端编辑页可以直接展示选中状态
     *
     * @param userId  当前登录用户ID
     * @param albumId 专辑ID
     * @return 专辑详情（含专辑标签集合）
     */
    AlbumInfo getAlbumInfo(Long userId, Long albumId);


    /**
     * 删除专辑
     * <p>
     * 只能删除自己的专辑；删除时连同专辑标签关系、专辑统计数据一起逻辑删除
     *
     * @param userId  当前登录用户ID
     * @param albumId 专辑ID
     */
    void removeAlbumInfo(Long userId, Long albumId);


    /**
     * 编辑专辑
     * <p>
     * 只更新表单里能改的字段（标题、分类、简介、封面、付费信息、标签等），
     * 声音数量、审核状态、归属用户等由服务端或其它模块维护的字段保持不变
     *
     * @param userId      当前登录用户ID
     * @param albumId     专辑ID
     * @param albumInfoVo 专辑信息
     */
    void updateAlbumInfo(Long userId, Long albumId, AlbumInfoVo albumInfoVo);


    /**
     * 根据专辑ID查询专辑（服务内部调用）
     * <p>
     * 搜索模块构建索引、渲染专辑详情页都要用，所以不做登录与归属校验，
     * 返回的 albumAttributeValueVoList 只带 attributeId 与 valueId（建索引只需要这两个值）
     *
     * @param albumId 专辑ID
     * @return 专辑信息，专辑不存在时返回 null
     */
    AlbumInfo getAlbumInfoById(Long albumId);


    /**
     * 查询专辑统计信息（服务内部调用）
     * <p>
     * 把 album_stat 里的播放量、订阅量、购买量、评论数四行汇总成一个对象，缺失的统计项按 0 处理
     *
     * @param albumId 专辑ID
     * @return 专辑统计信息
     */
    AlbumStatVo getAlbumStatVo(Long albumId);


    /**
     * 查询全部专辑ID（服务内部调用）
     * <p>
     * 搜索模块初始化索引时用它拿到需要建索引的专辑清单
     *
     * @return 专辑ID集合
     */
    List<Long> findAllAlbumIds();


    /**
     * 根据专辑ID集合批量查询专辑（服务内部调用）
     * <p>
     * 「我的-订阅」这类列表页一次要展示几十张专辑，逐条查会打出一串请求；
     * 只查专辑本体，不带标签集合
     *
     * @param albumIdList 专辑ID集合
     * @return 专辑集合，入参为空时返回空集合
     */
    List<AlbumInfo> findAlbumInfoByIds(List<Long> albumIdList);


    /**
     * 更新专辑统计信息（MQ 消费者调用）
     * <p>
     * 按 statType 对 album_stat 里对应的那一行做增量累加：专辑播放量、订阅量、
     * 购买量、评论数的变更都走这一个入口。统计行不存在时补插一条，避免老数据丢计数
     *
     * @param albumStatMqVo 统计变更消息（专辑ID、统计类型、增量）
     */
    void updateAlbumStat(AlbumStatMqVo albumStatMqVo);

}
