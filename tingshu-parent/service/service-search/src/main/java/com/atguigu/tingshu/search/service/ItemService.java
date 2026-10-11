package com.atguigu.tingshu.search.service;

import com.atguigu.tingshu.vo.search.AlbumDetailVo;

public interface ItemService {


    /**
     * 查询专辑详情
     * <p>
     * 详情数据来自专辑模块与用户模块，查询链路是「布隆过滤器 -> Redis 缓存 -> 数据库」，
     * 目的：挡住不存在的专辑请求、挡住重复查库
     *
     * @param albumId 专辑ID
     * @return 专辑详情，专辑不存在时返回 null
     */
    AlbumDetailVo getAlbumInfo(Long albumId);

}
