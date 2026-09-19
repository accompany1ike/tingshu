package com.atguigu.tingshu.album.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.BaseAttribute;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "分类管理")
@RestController
@RequestMapping(value="/api/album")
@SuppressWarnings({"all"})
public class BaseCategoryApiController {

	@Autowired
	private BaseCategoryService baseCategoryService;


	/**
	 * 查询所有分类（1、2、3级分类）
	 * 保存专辑时需要为新增专辑关联分类，三级分类数据采用列表（树形）展示
	 *
	 * @return 一、二、三级分类嵌套的树形集合
	 */
	@Operation(summary = "查询所有分类（1、2、3级分类）")
	@GetMapping("/category/getBaseCategoryList")
	public Result<List<JSONObject>> getBaseCategoryList() {
		List<JSONObject> list = baseCategoryService.getBaseCategoryList();
		return Result.ok(list);
	}


	/**
	 * 根据一级分类Id获取分类（标签名包含标签值） 列表
	 * 保存专辑时用于渲染该一级分类下的专辑标签
	 *
	 * @param category1Id 1级分类ID
	 * @return 标签集合，每个标签的 attributeValueList 为其标签值集合
	 */
	@Operation(summary = "根据一级分类Id获取分类（标签名包含标签值） 列表")
	@GetMapping("/category/findAttribute/{category1Id}")
	public Result<List<BaseAttribute>> getAttributesByCategory1Id(@PathVariable Long category1Id) {
		List<BaseAttribute> list = baseCategoryService.getAttributesByCategory1Id(category1Id);
		return Result.ok(list);
	}

}
