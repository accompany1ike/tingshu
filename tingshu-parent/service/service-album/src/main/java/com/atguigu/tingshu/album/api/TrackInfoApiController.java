package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.login.GuiGuLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackPaidSettingVo;
import com.atguigu.tingshu.vo.album.TrackStatVo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "声音管理")
@RestController
@RequestMapping("api/album")
@SuppressWarnings({"all"})
public class TrackInfoApiController {

	@Autowired
	private TrackInfoService trackInfoService;

	@Autowired
	private VodService vodService;


	/**
	 * 上传声音文件
	 * <p>
	 * 表单字段名固定为 file，与前端 cl-upload 组件的 fileName 默认值保持一致；
	 * 文件直接传给腾讯云点播，返回点播文件唯一标识与播放地址，此时还没有入库
	 *
	 * @param file 音频文件
	 * @return mediaFileId、mediaUrl、mediaDuration、mediaSize、mediaType
	 */
	@Operation(summary = "上传声音文件")
	@PostMapping("/trackInfo/uploadTrack")
	public Result<Map<String, Object>> uploadTrack(@RequestParam("file") MultipartFile file) {
		return Result.ok(vodService.uploadTrack(file));
	}


	/**
	 * 保存声音（新增）
	 * <p>
	 * 只能给自己创建的专辑添加声音，所属专辑不是自己的会被拒绝
	 *
	 * @param trackInfoVo 声音信息
	 * @return 新增声音ID
	 */
	@Operation(summary = "保存声音")
	@GuiGuLogin
	@PostMapping("/trackInfo/saveTrackInfo")
	public Result<Long> saveTrackInfo(@RequestBody @Validated TrackInfoVo trackInfoVo) {
		Long trackId = trackInfoService.saveTrackInfo(AuthContextHolder.getUserId(), trackInfoVo);
		return Result.ok(trackId);
	}


	/**
	 * 查询声音详情（编辑回显用）
	 * <p>
	 * 只能查询当前登录用户自己的声音
	 *
	 * @param trackId 声音ID
	 * @return 声音详情
	 */
	@Operation(summary = "查询声音详情")
	@GuiGuLogin
	@GetMapping("/trackInfo/getTrackInfo/{trackId}")
	public Result<TrackInfo> getTrackInfo(@PathVariable Long trackId) {
		return Result.ok(trackInfoService.getTrackInfo(AuthContextHolder.getUserId(), trackId));
	}


	/**
	 * 根据声音ID集合批量查询声音（服务内部调用，用户模块的收藏/历史列表使用）
	 * <p>
	 * 不加登录校验、也不校验声音归属：列表页展示的是用户自己收藏或听过的声音，
	 * 声音本身可能是别的主播上传的，按 getTrackInfo 的归属校验会全部查不出来
	 *
	 * @param trackIdList 声音ID集合
	 * @return 声音集合
	 */
	@Operation(summary = "批量查询声音（服务内部调用）")
	@PostMapping("/trackInfo/findTrackInfoByIds")
	public Result<List<TrackInfo>> findTrackInfoByIds(@RequestBody List<Long> trackIdList) {
		return Result.ok(trackInfoService.findTrackInfoByIds(trackIdList));
	}


	/**
	 * 编辑声音
	 * <p>
	 * 只能修改自己的声音；重新上传音频时媒体信息一起更新，未提交的字段保持库中原值
	 *
	 * @param trackId     声音ID
	 * @param trackInfoVo 声音信息
	 * @return 操作结果
	 */
	@Operation(summary = "编辑声音")
	@GuiGuLogin
	@PutMapping("/trackInfo/updateTrackInfo/{trackId}")
	public Result updateTrackInfo(@PathVariable Long trackId, @RequestBody @Validated TrackInfoVo trackInfoVo) {
		trackInfoService.updateTrackInfo(AuthContextHolder.getUserId(), trackId, trackInfoVo);
		return Result.ok();
	}


	/**
	 * 删除声音
	 * <p>
	 * 只能删除自己的声音；删除声音时连带清理它的统计数据，并把专辑的声音数量减一
	 *
	 * @param trackId 声音ID
	 * @return 操作结果
	 */
	@Operation(summary = "删除声音")
	@GuiGuLogin
	@DeleteMapping("/trackInfo/removeTrackInfo/{trackId}")
	public Result removeTrackInfo(@PathVariable Long trackId) {
		trackInfoService.removeTrackInfo(AuthContextHolder.getUserId(), trackId);
		return Result.ok();
	}


	/**
	 * 分页查询当前登录用户的声音列表（创作中心-我的作品-声音）
	 * <p>
	 * 返回的 data.records 即声音列表，最新添加的排在最前面；用户ID一律取登录态，
	 * 忽略请求参数里的 userId
	 *
	 * @param page           页码
	 * @param limit          每页条数
	 * @param trackInfoQuery 查询条件（status-声音状态、trackTitle-标题关键字）
	 * @return 分页结果
	 */
	@Operation(summary = "查询当前用户声音分页列表")
	@GuiGuLogin
	@PostMapping("/trackInfo/findUserTrackPage/{page}/{limit}")
	public Result<Page<TrackListVo>> findUserTrackPage(@PathVariable int page,
													   @PathVariable int limit,
													   @RequestBody(required = false) TrackInfoQuery trackInfoQuery) {
		Long userId = AuthContextHolder.getUserId();
		Page<TrackListVo> pageInfo = trackInfoService.findUserTrackPage(page, limit, userId, trackInfoQuery);
		return Result.ok(pageInfo);
	}


	/**
	 * 根据专辑ID分页查询声音列表（专辑详情页用）
	 * <p>
	 * 未登录也能浏览专辑详情，所以这里不强制登录（required=false）：未登录时拿不到 userId，
	 * 付费标识按"非VIP、未购买"处理。每条声音是否显示付费标识由服务端算好，前端只看 isShowPaidMark
	 *
	 * @param albumId 专辑ID
	 * @param page    页码
	 * @param limit   每页条数
	 * @return 分页结果，records 为声音列表（含付费标识）
	 */
	@Operation(summary = "根据专辑ID查询声音分页列表")
	@GuiGuLogin(required = false)
	@GetMapping("/trackInfo/findAlbumTrackPage/{albumId}/{page}/{limit}")
	public Result<Page<AlbumTrackListVo>> findAlbumTrackPage(@PathVariable Long albumId,
															 @PathVariable int page,
															 @PathVariable int limit) {
		Long userId = AuthContextHolder.getUserId();
		Page<AlbumTrackListVo> pageInfo = trackInfoService.findAlbumTrackPage(albumId, page, limit, userId);
		return Result.ok(pageInfo);
	}


	/**
	 * 查询声音统计信息（播放页用）
	 * <p>
	 * 播放量、收藏量、点赞量、评论数都是公开数据，不强制登录
	 *
	 * @param trackId 声音ID
	 * @return 声音统计信息
	 */
	@Operation(summary = "查询声音统计信息")
	@GuiGuLogin(required = false)
	@GetMapping("/trackInfo/getTrackStatVo/{trackId}")
	public Result<TrackStatVo> getTrackStatVo(@PathVariable Long trackId) {
		return Result.ok(trackInfoService.getTrackStatVo(trackId));
	}


	/**
	 * 查询某个声音的购买选项（详情页「购买本集」弹窗用）
	 * <p>
	 * 弹窗要展示两种买法的价格，属于公开的定价信息，不强制登录
	 *
	 * @param trackId 声音ID
	 * @return 购买选项列表（单集购买 / 整专辑购买）
	 */
	@Operation(summary = "查询声音购买选项")
	@GuiGuLogin(required = false)
	@GetMapping("/trackInfo/findUserTrackPaidList/{trackId}")
	public Result<List<TrackPaidSettingVo>> findUserTrackPaidList(@PathVariable Long trackId) {
		return Result.ok(trackInfoService.findUserTrackPaidList(trackId));
	}

}
