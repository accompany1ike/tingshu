package com.atguigu.tingshu.album;

import com.atguigu.tingshu.album.impl.AlbumDegradeFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * <p>
 * 专辑模块远程调用Feign接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-album", fallback = AlbumDegradeFeignClient.class)
public interface AlbumFeignClient {

    /**
     * 根据专辑ID查询专辑（含标签集合）
     * <p>
     * 搜索模块构建索引、渲染专辑详情时调用，接口不做登录校验，专辑不存在时 data 为 null
     *
     * @param albumId 专辑ID
     * @return 专辑信息
     */
    @GetMapping("/api/album/albumInfo/getAlbumInfoById/{albumId}")
    Result<AlbumInfo> getAlbumInfoById(@PathVariable Long albumId);

    /**
     * 查询专辑统计信息（播放量、订阅量、购买量、评论数）
     *
     * @param albumId 专辑ID
     * @return 专辑统计信息
     */
    @GetMapping("/api/album/albumInfo/getAlbumStatVo/{albumId}")
    Result<AlbumStatVo> getAlbumStatVo(@PathVariable Long albumId);

    /**
     * 查询全部专辑ID（搜索模块初始化索引时使用）
     *
     * @return 专辑ID集合
     */
    @GetMapping("/api/album/albumInfo/findAllAlbumIds")
    Result<List<Long>> findAllAlbumIds();

    /**
     * 根据三级分类Id查询一、二、三级分类信息
     *
     * @param category3Id 三级分类ID
     * @return 分类视图
     */
    @GetMapping("/api/album/category/getBaseCategoryView/{category3Id}")
    Result<BaseCategoryView> getBaseCategoryView(@PathVariable Long category3Id);

    /**
     * 根据一级分类Id查询置顶的三级分类列表
     *
     * @param category1Id 一级分类ID
     * @return 三级分类集合
     */
    @GetMapping("/api/album/category/findTopBaseCategory3/{category1Id}")
    Result<List<BaseCategory3>> findTopBaseCategory3(@PathVariable Long category1Id);

    /**
     * 根据专辑ID集合批量查询专辑
     * <p>
     * 「我的-订阅」这类列表页一次要展示几十张专辑，逐条调 getAlbumInfoById 会打出一串请求，
     * 这里一次查完；不查专辑标签，列表页用不到
     *
     * @param albumIdList 专辑ID集合
     * @return 专辑集合，入参为空时返回空集合
     */
    @PostMapping("/api/album/albumInfo/findAlbumInfoByIds")
    Result<List<AlbumInfo>> findAlbumInfoByIds(@RequestBody List<Long> albumIdList);

    /**
     * 根据声音ID集合批量查询声音
     * <p>
     * 「我的-收藏 / 历史」列表页需要声音标题、封面、时长，同样是批量查一次
     *
     * @param trackIdList 声音ID集合
     * @return 声音集合，入参为空时返回空集合
     */
    @PostMapping("/api/album/trackInfo/findTrackInfoByIds")
    Result<List<TrackInfo>> findTrackInfoByIds(@RequestBody List<Long> trackIdList);

}
