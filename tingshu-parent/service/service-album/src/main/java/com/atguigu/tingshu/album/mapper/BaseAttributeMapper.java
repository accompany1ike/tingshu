package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.BaseAttribute;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BaseAttributeMapper extends BaseMapper<BaseAttribute> {


    /**
     * 根据一级分类Id获取分类（标签名包含标签值） 列表
     *
     * @param category1Id 1级分类ID
     * @return 标签集合，每个标签的 attributeValueList 为其标签值集合
     */
    List<BaseAttribute> getAttributesByCategory1Id(@Param("category1Id") Long category1Id);

}
