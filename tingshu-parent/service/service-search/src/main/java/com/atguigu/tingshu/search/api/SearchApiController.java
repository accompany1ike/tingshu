package com.atguigu.tingshu.search.api;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.search.ChannelDataVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "搜索专辑管理")
@RestController
@RequestMapping("api/search")
@SuppressWarnings({"all"})
public class SearchApiController {

    @Autowired
    private SearchService searchService;


    /**
     * 检索专辑
     * <p>
     * 检索条件：关键字（专辑标题、简介、主播名称）、一/二/三级分类、专辑标签（标签id:标签值id）；
     * 排序方式：1:desc-综合排序（热度）2:desc-播放量 3:desc-发布时间，asc/desc 控制升降序
     *
     * @param albumIndexQuery 检索条件（含分页信息）
     * @return 检索结果（当前页专辑列表 + 总记录数 + 总页数）
     */
    @Operation(summary = "检索专辑")
    @PostMapping("/albumInfo")
    public Result<AlbumSearchResponseVo> searchAlbum(@RequestBody AlbumIndexQuery albumIndexQuery) {
        return Result.ok(searchService.searchAlbum(albumIndexQuery));
    }


    /**
     * 搜索建议（输入联想）
     * <p>
     * 中文、全拼、拼音首字母都能联想，例如「三国」「sanguo」「sgyy」都能提示出对应专辑
     *
     * @param keyword 用户输入的关键字
     * @return 建议词集合
     */
    @Operation(summary = "搜索建议")
    @GetMapping("/albumInfo/completeSuggest/{keyword}")
    public Result<List<String>> completeSuggest(@PathVariable String keyword) {
        return Result.ok(searchService.completeSuggest(keyword));
    }


    /**
     * 查询频道页数据（首页）
     *
     * @param category1Id 一级分类ID
     * @return 按三级分类分段的专辑集合
     */
    @Operation(summary = "查询频道页数据")
    @GetMapping("/albumInfo/channel/{category1Id}")
    public Result<List<ChannelDataVo>> getChannelData(@PathVariable Long category1Id) {
        return Result.ok(searchService.getChannelData(category1Id));
    }


    /**
     * 查询排行榜
     *
     * @param category1Id 一级分类ID
     * @param dimension   排序维度：hotScore-热度 playStatNum-播放量 subscribeStatNum-订阅量
     *                    buyStatNum-购买量 commentStatNum-评论数
     * @return 排行榜专辑集合
     */
    @Operation(summary = "查询排行榜")
    @GetMapping("/albumInfo/findRankingList/{category1Id}/{dimension}")
    public Result<List<AlbumInfoIndexVo>> findRankingList(@PathVariable Long category1Id,
                                                         @PathVariable String dimension) {
        return Result.ok(searchService.findRankingList(category1Id, dimension));
    }


    /**
     * 专辑上架：手工触发建立索引
     * <p>
     * 正常流程由专辑模块通过 MQ 触发，这个接口用于排查索引问题或补数据
     *
     * @param albumId 专辑ID
     * @return 操作结果
     */
    @Operation(summary = "专辑上架（建立索引）")
    @GetMapping("/albumInfo/upperAlbum/{albumId}")
    public Result upperAlbum(@PathVariable Long albumId) {
        searchService.upperAlbum(albumId);
        return Result.ok();
    }


    /**
     * 专辑下架：手工触发删除索引
     *
     * @param albumId 专辑ID
     * @return 操作结果
     */
    @Operation(summary = "专辑下架（删除索引）")
    @GetMapping("/albumInfo/lowerAlbum/{albumId}")
    public Result lowerAlbum(@PathVariable Long albumId) {
        searchService.lowerAlbum(albumId);
        return Result.ok();
    }


    /**
     * 全量初始化专辑索引
     * <p>
     * 索引库首次搭建或数据修复时使用，会把库里所有专辑重新灌一遍索引，专辑多时耗时较长
     *
     * @return 成功建立索引的专辑数量
     */
    @Operation(summary = "全量初始化专辑索引")
    @PostMapping("/albumInfo/initAlbumIndex")
    public Result<Integer> initAlbumIndex() {
        return Result.ok(searchService.initAlbumIndex());
    }

}
