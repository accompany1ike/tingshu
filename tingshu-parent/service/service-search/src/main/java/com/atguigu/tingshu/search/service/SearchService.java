package com.atguigu.tingshu.search.service;

import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.search.ChannelDataVo;

import java.util.List;

public interface SearchService {


    /**
     * 专辑上架（建立索引）
     * <p>
     * 专辑新增、编辑、专辑下声音数量变化时由 MQ 触发：
     * 远程调用专辑模块与用户模块补齐索引数据，写入索引库与搜索建议库
     *
     * @param albumId 专辑ID
     */
    void upperAlbum(Long albumId);


    /**
     * 专辑下架（删除索引）
     *
     * @param albumId 专辑ID
     */
    void lowerAlbum(Long albumId);


    /**
     * 刷新索引里的专辑统计与热度
     * <p>
     * 播放、订阅、购买、评论的累加只落在专辑库，这里把索引里的统计字段同步过去，
     * 否则排行榜（按播放量/订阅量/购买量/评论数排）和综合排序用的都是建索引那一刻的旧数字
     *
     * @param albumId 专辑ID
     */
    void updateAlbumStatIndex(Long albumId);


    /**
     * 检索专辑
     * <p>
     * 检索条件：关键字（专辑标题、简介、主播名称）、一/二/三级分类、专辑标签；
     * 排序方式：1-综合排序（热度）2-播放量 3-发布时间，方向 asc/desc
     *
     * @param albumIndexQuery 检索条件（含分页信息）
     * @return 检索结果（当前页专辑列表 + 总记录数 + 总页数）
     */
    AlbumSearchResponseVo searchAlbum(AlbumIndexQuery albumIndexQuery);


    /**
     * 搜索建议（输入联想）
     * <p>
     * 支持中文、全拼、拼音首字母三种输入方式，走 ES 的 completion suggester
     *
     * @param keyword 用户输入的关键字
     * @return 建议词集合（已去重）
     */
    List<String> completeSuggest(String keyword);


    /**
     * 查询频道页数据（首页）
     * <p>
     * 按一级分类下的三级分类分段返回专辑，每段是该三级分类下热度最高的若干张专辑
     *
     * @param category1Id 一级分类ID
     * @return 频道页分段数据
     */
    List<ChannelDataVo> getChannelData(Long category1Id);


    /**
     * 查询排行榜
     * <p>
     * 取一级分类下按指定维度倒序的前若干张专辑
     *
     * @param category1Id 一级分类ID
     * @param dimension   排序维度：hotScore-热度 playStatNum-播放量 subscribeStatNum-订阅量
     *                    buyStatNum-购买量 commentStatNum-评论数
     * @return 排行榜专辑集合
     */
    List<AlbumInfoIndexVo> findRankingList(Long category1Id, String dimension);


    /**
     * 全量初始化专辑索引
     * <p>
     * 一次性把库里所有专辑灌进索引库，用于索引库首次搭建或数据修复，耗时较长
     *
     * @return 成功建立索引的专辑数量
     */
    int initAlbumIndex();

}
