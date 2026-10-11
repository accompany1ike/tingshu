package com.atguigu.tingshu.album.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory1;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BaseCategoryService extends IService<BaseCategory1> {


    /**
     * 查询所有分类（1、2、3级分类）
     * <p>
     * 保存专辑时需要为新增专辑关联分类，三级分类数据采用列表（树形）展示，返回结构：
     * [{categoryId:一级分类ID, categoryName:一级分类名称,
     * categoryChild:[{categoryId:二级分类ID, categoryName:二级分类名称,
     * categoryChild:[{categoryId:三级分类ID, categoryName:三级分类名称}]}]}]
     *
     * @return 一、二、三级分类嵌套的树形集合
     */
    List<JSONObject> getBaseCategoryList();


    /**
     * 根据一级分类Id获取分类（标签名包含标签值） 列表
     * <p>
     * 保存专辑时用于渲染该一级分类下的专辑标签（标签-标签值 一对多）
     *
     * @param category1Id 1级分类ID
     * @return 标签集合，每个标签的 attributeValueList 为其标签值集合
     */
    List<BaseAttribute> getAttributesByCategory1Id(Long category1Id);


    /**
     * 根据三级分类Id查询一、二、三级分类信息（服务内部调用）
     * <p>
     * 专辑详情页要展示「一级分类 / 二级分类 / 三级分类」，建索引时也要把一级、二级分类ID写进索引，
     * 数据取自分类视图 base_category_view
     *
     * @param category3Id 三级分类ID
     * @return 分类视图，分类不存在时返回 null
     */
    BaseCategoryView getBaseCategoryView(Long category3Id);


    /**
     * 根据一级分类Id查询置顶的三级分类（服务内部调用）
     * <p>
     * 首页频道页按三级分类分段展示专辑，返回的集合既是频道页的分段依据，
     * 也是「二级分类快捷入口」的展示数据
     *
     * @param category1Id 一级分类ID
     * @return 三级分类集合，按排序值升序
     */
    List<BaseCategory3> findTopBaseCategory3(Long category1Id);

}
