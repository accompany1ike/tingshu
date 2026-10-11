package com.atguigu.tingshu.search.api;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.search.service.ItemService;
import com.atguigu.tingshu.vo.search.AlbumDetailVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "专辑详情管理")
@RestController
@RequestMapping("api/search")
@SuppressWarnings({"all"})
public class itemApiController {

	@Autowired
	private ItemService itemService;


	/**
	 * 查询专辑详情
	 * <p>
	 * 详情页的一次性数据入口：专辑信息（含价格、付费类型）、分类、统计信息、主播信息
	 *
	 * @param albumId 专辑ID
	 * @return 专辑详情
	 */
	@Operation(summary = "查询专辑详情")
	@GetMapping("/albumInfo/{albumId}")
	public Result<AlbumDetailVo> getAlbumInfo(@PathVariable Long albumId) {
		AlbumDetailVo albumDetailVo = itemService.getAlbumInfo(albumId);
		if (albumDetailVo == null) {
			throw new GuiguException(ResultCodeEnum.DATA_ERROR.getCode(), "专辑不存在或已下架");
		}
		return Result.ok(albumDetailVo);
	}

}
