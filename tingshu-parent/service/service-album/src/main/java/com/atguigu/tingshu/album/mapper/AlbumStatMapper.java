package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.AlbumStat;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AlbumStatMapper extends BaseMapper<AlbumStat> {

	/**
	 * 原子自增专辑统计值
	 * <p>
	 * 播放量是高频并发更新（每个用户每 10 秒上报一次），必须交给数据库做
	 * 「读取 + 累加 + 写回」的原子操作，不能先查出来再 setNum 回写，
	 * 否则并发下会互相覆盖、计数偏小。
	 * <p>
	 * stat_num 允许为空，用 ifnull 兜底成 0，避免整行被算成 null
	 *
	 * @param albumId  专辑ID
	 * @param statType 统计类型：0401-播放量 0402-订阅量 0403-购买量 0404-评论数
	 * @param count    增量
	 * @return 影响行数：0 表示没有对应的统计行，调用方需要补插一条
	 */
	@Update("update album_stat set stat_num = ifnull(stat_num, 0) + #{count}, update_time = now() "
			+ "where album_id = #{albumId} and stat_type = #{statType} and is_deleted = 0")
	int increaseStatNum(@Param("albumId") Long albumId,
						@Param("statType") String statType,
						@Param("count") Integer count);

}
