package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "专辑管理")
@RestController
@RequestMapping("api/album")
@SuppressWarnings({"all"})
public class AlbumInfoApiController {

	@Autowired
	private AlbumInfoService albumInfoService;


	/**
	 * 保存专辑（新增）
	 * <p>
	 * 需要登录：未登录时由 @GuiGuLogin 切面直接响应208，前端据此引导用户重新登录
	 *
	 * @param albumInfoVo 专辑信息
	 * @return 新增专辑ID
	 */
	@Operation(summary = "保存专辑")
	@GuiGuLogin
	@PostMapping("/albumInfo/saveAlbumInfo")
	public Result<Long> saveAlbumInfo(@RequestBody @Validated AlbumInfoVo albumInfoVo) {
		Long userId = AuthContextHolder.getUserId();
		Long albumId = albumInfoService.saveAlbumInfo(userId, albumInfoVo);
		return Result.ok(albumId);
	}


	/**
	 * 分页查询当前登录用户的专辑列表（创作中心-我的作品）
	 * <p>
	 * 返回的 data.records 即专辑列表，最新添加的排在最前面；不需要登录以外的人查别人的专辑，
	 * 因此用户ID一律取登录态，忽略请求参数里的 userId
	 *
	 * @param page           页码
	 * @param limit          每页条数
	 * @param albumInfoQuery 查询条件（status-专辑状态、albumTitle-标题关键字）
	 * @return 分页结果
	 */
	@Operation(summary = "查询当前用户专辑分页列表")
	@GuiGuLogin
	@PostMapping("/albumInfo/findUserAlbumPage/{page}/{limit}")
	public Result<Page<AlbumListVo>> findUserAlbumPage(@PathVariable int page,
													   @PathVariable int limit,
													   @RequestBody(required = false) AlbumInfoQuery albumInfoQuery) {
		Long userId = AuthContextHolder.getUserId();
		Page<AlbumListVo> pageInfo = albumInfoService.findUserAlbumPage(page, limit, userId, albumInfoQuery);
		return Result.ok(pageInfo);
	}


	/**
	 * 查询当前登录用户的全部专辑（新增/编辑声音时选择所属专辑用）
	 *
	 * @return 当前用户的专辑集合（只含ID与标题）
	 */
	@Operation(summary = "查询当前用户全部专辑")
	@GuiGuLogin
	@GetMapping("/albumInfo/findUserAllAlbumList")
	public Result<List<AlbumInfo>> findUserAllAlbumList() {
		return Result.ok(albumInfoService.findUserAllAlbumList(AuthContextHolder.getUserId()));
	}


	/**
	 * 查询专辑详情（编辑回显用）
	 * <p>
	 * 只能查询当前登录用户自己的专辑；返回的 albumAttributeValueVoList 已带标签名与标签值名
	 *
	 * @param albumId 专辑ID
	 * @return 专辑详情
	 */
	@Operation(summary = "查询专辑详情")
	@GuiGuLogin
	@GetMapping("/albumInfo/getAlbumInfo/{albumId}")
	public Result<AlbumInfo> getAlbumInfo(@PathVariable Long albumId) {
		return Result.ok(albumInfoService.getAlbumInfo(AuthContextHolder.getUserId(), albumId));
	}


	/**
	 * 编辑专辑
	 * <p>
	 * 只能修改自己的专辑；标签按提交内容整体替换，未提交的字段保持库中原值
	 *
	 * @param albumId     专辑ID
	 * @param albumInfoVo 专辑信息
	 * @return 操作结果
	 */
	@Operation(summary = "编辑专辑")
	@GuiGuLogin
	@PutMapping("/albumInfo/updateAlbumInfo/{albumId}")
	public Result updateAlbumInfo(@PathVariable Long albumId, @RequestBody @Validated AlbumInfoVo albumInfoVo) {
		albumInfoService.updateAlbumInfo(AuthContextHolder.getUserId(), albumId, albumInfoVo);
		return Result.ok();
	}


	/**
	 * 删除专辑
	 * <p>
	 * 只能删除自己的专辑；删除专辑时连带清理它的标签关系和统计数据
	 *
	 * @param albumId 专辑ID
	 * @return 操作结果
	 */
	@Operation(summary = "删除专辑")
	@GuiGuLogin
	@DeleteMapping("/albumInfo/removeAlbumInfo/{albumId}")
	public Result removeAlbumInfo(@PathVariable Long albumId) {
		albumInfoService.removeAlbumInfo(AuthContextHolder.getUserId(), albumId);
		return Result.ok();
	}


	/**
	 * 根据专辑ID查询专辑（服务内部调用，搜索模块使用）
	 * <p>
	 * 不加登录校验：搜索模块建索引、渲染专辑详情时并没有用户登录态；
	 * 返回的标签集合只带 attributeId 与 valueId，专辑不存在时 data 为 null
	 *
	 * @param albumId 专辑ID
	 * @return 专辑信息
	 */
	@Operation(summary = "根据专辑ID查询专辑（服务内部调用）")
	@GetMapping("/albumInfo/getAlbumInfoById/{albumId}")
	public Result<AlbumInfo> getAlbumInfoById(@PathVariable Long albumId) {
		return Result.ok(albumInfoService.getAlbumInfoById(albumId));
	}


	/**
	 * 根据专辑ID集合批量查询专辑（服务内部调用，用户模块的订阅列表使用）
	 * <p>
	 * 不加登录校验：列表页一次要展示几十张专辑，逐条调会打出一串请求，这里一次查完
	 *
	 * @param albumIdList 专辑ID集合
	 * @return 专辑集合
	 */
	@Operation(summary = "批量查询专辑（服务内部调用）")
	@PostMapping("/albumInfo/findAlbumInfoByIds")
	public Result<List<AlbumInfo>> findAlbumInfoByIds(@RequestBody List<Long> albumIdList) {
		return Result.ok(albumInfoService.findAlbumInfoByIds(albumIdList));
	}


	/**
	 * 查询专辑统计信息（服务内部调用，搜索模块使用）
	 *
	 * @param albumId 专辑ID
	 * @return 专辑统计信息
	 */
	@Operation(summary = "查询专辑统计信息（服务内部调用）")
	@GetMapping("/albumInfo/getAlbumStatVo/{albumId}")
	public Result<AlbumStatVo> getAlbumStatVo(@PathVariable Long albumId) {
		return Result.ok(albumInfoService.getAlbumStatVo(albumId));
	}


	/**
	 * 查询全部专辑ID（服务内部调用，搜索模块初始化索引使用）
	 *
	 * @return 专辑ID集合
	 */
	@Operation(summary = "查询全部专辑ID（服务内部调用）")
	@GetMapping("/albumInfo/findAllAlbumIds")
	public Result<List<Long>> findAllAlbumIds() {
		return Result.ok(albumInfoService.findAllAlbumIds());
	}

}
