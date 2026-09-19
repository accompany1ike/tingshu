package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
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

}
