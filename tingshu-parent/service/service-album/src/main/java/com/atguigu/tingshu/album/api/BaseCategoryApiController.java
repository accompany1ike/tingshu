package com.atguigu.tingshu.album.api;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
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


	/**
	 * 根据一级分类Id查询置顶的三级分类列表
	 * 首页频道页的「二级分类快捷入口」用它渲染，搜索模块组织频道页数据时也用它补齐分类名称
	 *
	 * @param category1Id 一级分类ID
	 * @return 三级分类集合
	 */
	@Operation(summary = "根据一级分类Id查询置顶的三级分类列表")
	@GetMapping("/category/findTopBaseCategory3/{category1Id}")
	public Result<List<BaseCategory3>> findTopBaseCategory3(@PathVariable Long category1Id) {
		return Result.ok(baseCategoryService.findTopBaseCategory3(category1Id));
	}


	/**
	 * 根据三级分类Id查询一、二、三级分类信息（服务内部调用）
	 * 专辑详情页展示分类、搜索模块建索引时确定一二级分类ID，都用这个接口
	 *
	 * @param category3Id 三级分类ID
	 * @return 分类视图
	 */
	@Operation(summary = "根据三级分类Id查询分类信息（服务内部调用）")
	@GetMapping("/category/getBaseCategoryView/{category3Id}")
	public Result<BaseCategoryView> getBaseCategoryView(@PathVariable Long category3Id) {
		return Result.ok(baseCategoryService.getBaseCategoryView(category3Id));
	}

}
