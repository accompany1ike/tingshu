package com.atguigu.tingshu.album.impl;


import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 专辑模块远程调用失败时的兜底实现（服务降级）
 * <p>
 * 返回空数据而不是抛异常：搜索模块宁可少一条索引数据，也不能因为专辑服务抖动就整体失败
 */
@Slf4j
@Component
public class AlbumDegradeFeignClient implements AlbumFeignClient {

    @Override
    public Result<AlbumInfo> getAlbumInfoById(Long albumId) {
        log.error("查询专辑信息失败，服务降级，专辑ID：{}", albumId);
        return Result.ok(null);
    }

    @Override
    public Result<AlbumStatVo> getAlbumStatVo(Long albumId) {
        log.error("查询专辑统计信息失败，服务降级，专辑ID：{}", albumId);
        return Result.ok(null);
    }

    @Override
    public Result<List<Long>> findAllAlbumIds() {
        log.error("查询全部专辑ID失败，服务降级");
        return Result.ok(null);
    }

    @Override
    public Result<BaseCategoryView> getBaseCategoryView(Long category3Id) {
        log.error("查询分类信息失败，服务降级，三级分类ID：{}", category3Id);
        return Result.ok(null);
    }

    @Override
    public Result<List<BaseCategory3>> findTopBaseCategory3(Long category1Id) {
        log.error("查询置顶三级分类失败，服务降级，一级分类ID：{}", category1Id);
        return Result.ok(null);
    }

    @Override
    public Result<List<AlbumInfo>> findAlbumInfoByIds(List<Long> albumIdList) {
        log.error("批量查询专辑信息失败，服务降级，专辑数量：{}", albumIdList == null ? 0 : albumIdList.size());
        return Result.ok(null);
    }

    @Override
    public Result<List<TrackInfo>> findTrackInfoByIds(List<Long> trackIdList) {
        log.error("批量查询声音信息失败，服务降级，声音数量：{}", trackIdList == null ? 0 : trackIdList.size());
        return Result.ok(null);
    }

}
