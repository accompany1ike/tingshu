package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.TrackStat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TrackStatMapper extends BaseMapper<TrackStat> {

	/**
	 * 原子自增声音统计值
	 * <p>
	 * 与专辑统计同理，播放量并发更新必须走数据库原子累加，避免并发覆盖导致计数偏小。
	 * stat_num 允许为空，用 ifnull 兜底成 0
	 *
	 * @param trackId  声音ID
	 * @param statType 统计类型：0701-播放量 0702-收藏量 0703-点赞量 0704-评论数
	 * @param count    增量
	 * @return 影响行数：0 表示没有对应的统计行，调用方需要补插一条
	 */
	@Update("update track_stat set stat_num = ifnull(stat_num, 0) + #{count}, update_time = now() "
			+ "where track_id = #{trackId} and stat_type = #{statType} and is_deleted = 0")
	int increaseStatNum(@Param("trackId") Long trackId,
						@Param("statType") String statType,
						@Param("count") Integer count);

}
