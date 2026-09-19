package com.atguigu.tingshu.album.service;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory1;
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

}
