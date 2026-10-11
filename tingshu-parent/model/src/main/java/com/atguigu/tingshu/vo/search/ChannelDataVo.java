package com.atguigu.tingshu.vo.search;

import com.atguigu.tingshu.model.album.BaseCategory3;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 频道页数据：一个三级分类 + 该分类下的专辑集合
 * <p>
 * 首页频道页是按三级分类分段展示专辑的（一个分类一行，行内是专辑卡片），
 * 所以返回的是「分段集合」，每段包含三级分类本身和它下面的专辑
 */
@Data
@Schema(description = "频道页数据（按三级分类分组）")
public class ChannelDataVo implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "三级分类")
	private BaseCategory3 baseCategory3;

	@Schema(description = "该三级分类下的专辑集合")
	private List<AlbumInfoIndexVo> list;

}
