package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TrackInfoMapper extends BaseMapper<TrackInfo> {

	/**
	 * 分页查询专辑下的声音列表（含播放量、评论数）
	 * <p>
	 * 声音统计在 track_stat 里是按行存的（一行一个统计类型），这里用两次 join 把它摊平成列，
	 * 避免查出声音后再逐条回查统计；分页由 MyBatis-Plus 的分页拦截器接管，第一个参数必须是 IPage
	 *
	 * @param pageParam 分页参数（页码、每页条数）
	 * @param albumId   专辑ID
	 * @return 分页结果，按集数（order_num）正序
	 */
	Page<AlbumTrackListVo> selectAlbumTrackPage(Page<AlbumTrackListVo> pageParam, @Param("albumId") Long albumId);

}
