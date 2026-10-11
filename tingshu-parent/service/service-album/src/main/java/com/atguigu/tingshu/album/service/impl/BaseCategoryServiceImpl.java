package com.atguigu.tingshu.album.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.tingshu.album.mapper.BaseAttributeMapper;
import com.atguigu.tingshu.album.mapper.BaseCategory1Mapper;
import com.atguigu.tingshu.album.mapper.BaseCategory2Mapper;
import com.atguigu.tingshu.album.mapper.BaseCategory3Mapper;
import com.atguigu.tingshu.album.mapper.BaseCategoryViewMapper;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseCategory1;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class BaseCategoryServiceImpl extends ServiceImpl<BaseCategory1Mapper, BaseCategory1> implements BaseCategoryService {

	@Autowired
	private BaseCategory1Mapper baseCategory1Mapper;

	@Autowired
	private BaseCategory2Mapper baseCategory2Mapper;

	@Autowired
	private BaseCategory3Mapper baseCategory3Mapper;

	@Autowired
	private BaseCategoryViewMapper baseCategoryViewMapper;

	@Autowired
	private BaseAttributeMapper baseAttributeMapper;


	/**
	 * 查询所有分类（1、2、3级分类）
	 * tips：数据从封装分类视图 base_category_view 中获取，在业务代码中进行组装
	 *
	 * @return 一、二、三级分类嵌套的树形集合
	 */
	@Override
	public List<JSONObject> getBaseCategoryList() {
		//1.查询分类视图得到所有分类数据（共计401条记录：15个一级、92个二级、401个三级）
		//  按各级分类ID排序，保证返回给页面的列表顺序稳定
		LambdaQueryWrapper<BaseCategoryView> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.orderByAsc(BaseCategoryView::getCategory1Id)
				.orderByAsc(BaseCategoryView::getCategory2Id)
				.orderByAsc(BaseCategoryView::getCategory3Id);
		List<BaseCategoryView> allCategoryList = baseCategoryViewMapper.selectList(queryWrapper);
		if (CollectionUtils.isEmpty(allCategoryList)) {
			return new ArrayList<>();
		}

		//2.处理一级分类：对所有分类数据根据一级分类ID分组
		//  Map-key:一级分类ID  Map-value:该一级分类下的全部分类视图数据（采用LinkedHashMap保证列表顺序与ID顺序一致）
		Map<Long, List<BaseCategoryView>> category1Map = allCategoryList.stream()
				.filter(categoryView -> categoryView.getCategory1Id() != null)
				.collect(Collectors.groupingBy(BaseCategoryView::getCategory1Id, LinkedHashMap::new, Collectors.toList()));

		//3.遍历每个一级分类，构建一、二、三级分类嵌套的树形集合
		List<JSONObject> allList = new ArrayList<>(category1Map.size());
		for (Map.Entry<Long, List<BaseCategoryView>> entry1 : category1Map.entrySet()) {
			//3.1 获取一级分类ID以及名称，构建一级分类JSON对象
			JSONObject jsonObject1 = new JSONObject();
			jsonObject1.put("categoryId", entry1.getKey());
			jsonObject1.put("categoryName", entry1.getValue().get(0).getCategory1Name());

			//4.在当前一级分类内，处理二级分类：对该一级分类集合根据二级分类ID分组
			Map<Long, List<BaseCategoryView>> category2Map = entry1.getValue().stream()
					.filter(categoryView -> categoryView.getCategory2Id() != null)
					.collect(Collectors.groupingBy(BaseCategoryView::getCategory2Id, LinkedHashMap::new, Collectors.toList()));

			List<JSONObject> jsonObject2List = new ArrayList<>(category2Map.size());
			for (Map.Entry<Long, List<BaseCategoryView>> entry2 : category2Map.entrySet()) {
				//4.1 获取二级分类ID以及名称，构建二级分类JSON对象
				JSONObject jsonObject2 = new JSONObject();
				jsonObject2.put("categoryId", entry2.getKey());
				jsonObject2.put("categoryName", entry2.getValue().get(0).getCategory2Name());

				//5.在当前二级分类内，处理三级分类：遍历该二级分类下的分类视图数据
				List<JSONObject> jsonObject3List = new ArrayList<>(entry2.getValue().size());
				for (BaseCategoryView baseCategoryView : entry2.getValue()) {
					if (baseCategoryView.getCategory3Id() == null) {
						continue;
					}
					//5.1 获取三级分类ID以及名称，构建三级分类JSON对象
					JSONObject jsonObject3 = new JSONObject();
					jsonObject3.put("categoryId", baseCategoryView.getCategory3Id());
					jsonObject3.put("categoryName", baseCategoryView.getCategory3Name());
					jsonObject3List.add(jsonObject3);
				}
				//5.2 将三级分类集合放入二级分类对象的categoryChild属性中
				jsonObject2.put("categoryChild", jsonObject3List);
				jsonObject2List.add(jsonObject2);
			}
			//4.2 将二级分类集合放入一级分类对象的categoryChild属性中
			jsonObject1.put("categoryChild", jsonObject2List);
			//3.2 将一级分类对象加入到目标集合中
			allList.add(jsonObject1);
		}
		return allList;
	}


	/**
	 * 根据一级分类Id获取分类（标签名包含标签值） 列表
	 *
	 * @param category1Id 1级分类ID
	 * @return 标签集合，每个标签的 attributeValueList 为其标签值集合
	 */
	@Override
	public List<BaseAttribute> getAttributesByCategory1Id(Long category1Id) {
		//调用持久层动态SQL完成标签-标签值的一对多查询
		return baseAttributeMapper.getAttributesByCategory1Id(category1Id);
	}


	/**
	 * 根据三级分类Id查询一、二、三级分类信息（服务内部调用）
	 *
	 * @param category3Id 三级分类ID
	 * @return 分类视图，分类不存在时返回 null
	 */
	@Override
	public BaseCategoryView getBaseCategoryView(Long category3Id) {
		if (category3Id == null) {
			return null;
		}
		return baseCategoryViewMapper.selectOne(new LambdaQueryWrapper<BaseCategoryView>()
				.eq(BaseCategoryView::getCategory3Id, category3Id)
				.last("limit 1"));
	}


	/**
	 * 根据一级分类Id查询置顶的三级分类（服务内部调用）
	 * <p>
	 * 分类视图里已经带了一级分类ID，直接按它筛出该一级分类下的全部三级分类，
	 * 再去 base_category3 里按置顶标记与排序值取一次，保证频道页分段顺序稳定
	 *
	 * @param category1Id 一级分类ID
	 * @return 三级分类集合，按排序值升序
	 */
	@Override
	public List<BaseCategory3> findTopBaseCategory3(Long category1Id) {
		if (category1Id == null) {
			return new ArrayList<>();
		}
		//1.先通过分类视图拿到该一级分类下所有三级分类ID
		List<Long> category3Ids = baseCategoryViewMapper.selectList(new LambdaQueryWrapper<BaseCategoryView>()
						.select(BaseCategoryView::getCategory3Id)
						.eq(BaseCategoryView::getCategory1Id, category1Id))
				.stream().map(BaseCategoryView::getCategory3Id)
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		if (CollectionUtils.isEmpty(category3Ids)) {
			return new ArrayList<>();
		}

		//2.按置顶标记与排序值查出三级分类（is_top=1 的排在前面）
		return baseCategory3Mapper.selectList(new LambdaQueryWrapper<BaseCategory3>()
				.in(BaseCategory3::getId, category3Ids)
				.orderByDesc(BaseCategory3::getIsTop)
				.orderByAsc(BaseCategory3::getOrderNum)
				.orderByAsc(BaseCategory3::getId));
	}

}
