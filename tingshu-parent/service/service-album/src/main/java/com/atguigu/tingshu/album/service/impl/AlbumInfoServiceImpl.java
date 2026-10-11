package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.BaseAttributeMapper;
import com.atguigu.tingshu.album.mapper.BaseAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.BaseCategoryViewMapper;
import com.atguigu.tingshu.album.mq.AlbumIndexMqSender;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.BaseAttribute;
import com.atguigu.tingshu.model.album.BaseAttributeValue;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumAttributeValueVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatMqVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class AlbumInfoServiceImpl extends ServiceImpl<AlbumInfoMapper, AlbumInfo> implements AlbumInfoService {

	/**
	 * 折扣允许的取值区间（不打折用 -1 表示）
	 */
	private static final BigDecimal DISCOUNT_MIN = new BigDecimal("0.1");
	private static final BigDecimal DISCOUNT_MAX = new BigDecimal("9.9");
	private static final BigDecimal DISCOUNT_NONE = new BigDecimal("-1");

	/**
	 * 允许的价格类型
	 */
	private static final Set<String> PRICE_TYPES = Set.of(SystemConstant.ALBUM_PRICE_TYPE_ONE,
			SystemConstant.ALBUM_PRICE_TYPE_ALL);

	@Autowired
	private AlbumInfoMapper albumInfoMapper;

	@Autowired
	private AlbumAttributeValueMapper albumAttributeValueMapper;

	@Autowired
	private AlbumStatMapper albumStatMapper;

	@Autowired
	private BaseCategoryViewMapper baseCategoryViewMapper;

	@Autowired
	private BaseAttributeMapper baseAttributeMapper;

	@Autowired
	private BaseAttributeValueMapper baseAttributeValueMapper;

	@Autowired
	private AlbumIndexMqSender albumIndexMqSender;


	/**
	 * 保存专辑（新增）
	 * <p>
	 * 一次提交需要落三张表：专辑、专辑标签关系、专辑统计，全部在一个事务里完成
	 *
	 * @param userId      当前登录用户ID
	 * @param albumInfoVo 专辑信息
	 * @return 新增专辑ID
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long saveAlbumInfo(Long userId, AlbumInfoVo albumInfoVo) {
		//1.校验分类：三级分类必须真实存在，并顺带取出它所属的一级分类（校验标签时要按一级分类归属）
		BaseCategoryView categoryView = baseCategoryViewMapper.selectOne(new LambdaQueryWrapper<BaseCategoryView>()
				.eq(BaseCategoryView::getCategory3Id, albumInfoVo.getCategory3Id())
				.last("limit 1"));
		if (categoryView == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "所选专辑分类不存在，请重新选择");
		}

		//2.校验付费规则：付费/vip免费专辑必须给出价格类型和大于0的价格（与前端表单校验保持一致）
		this.checkPayRule(albumInfoVo);

		//3.保存专辑
		AlbumInfo albumInfo = this.buildAlbumInfo(userId, albumInfoVo);
		albumInfoMapper.insert(albumInfo);
		Long albumId = albumInfo.getId();
		log.info("新增专辑成功，专辑ID：{}，用户ID：{}，三级分类：{}", albumId, userId, albumInfoVo.getCategory3Id());

		//4.保存专辑标签关系
		this.saveAlbumAttributeValue(albumId, categoryView.getCategory1Id(), albumInfoVo.getAlbumAttributeValueVoList());

		//5.初始化专辑统计信息：播放量、订阅量、购买量、评论数
		this.saveAlbumStat(albumId, SystemConstant.ALBUM_STAT_PLAY);
		this.saveAlbumStat(albumId, SystemConstant.ALBUM_STAT_SUBSCRIBE);
		this.saveAlbumStat(albumId, SystemConstant.ALBUM_STAT_BUY);
		this.saveAlbumStat(albumId, SystemConstant.ALBUM_STAT_COMMENT);

		//6.通知搜索模块建立索引（事务提交后发送，避免索引里查到的还是提交前的旧数据）
		albumIndexMqSender.sendUpperAlbum(albumId);

		return albumId;
	}

	/**
	 * 分页查询当前登录用户的专辑列表
	 * <p>
	 * 最新添加的排在最前面：专辑ID是自增的，按ID倒序等价于按创建时间倒序，
	 * 而且同一秒内创建的专辑也不会出现顺序错乱
	 *
	 * @param page           页码
	 * @param limit          每页条数
	 * @param userId         当前登录用户ID
	 * @param albumInfoQuery 查询条件（专辑状态、标题关键字）
	 * @return 分页结果
	 */
	@Override
	public Page<AlbumListVo> findUserAlbumPage(int page, int limit, Long userId, AlbumInfoQuery albumInfoQuery) {
		AlbumInfoQuery query = albumInfoQuery == null ? new AlbumInfoQuery() : albumInfoQuery;

		//1.分页查询专辑：只查当前登录用户的（不信任请求参数里的userId），支持按状态过滤和标题模糊查询
		LambdaQueryWrapper<AlbumInfo> queryWrapper = new LambdaQueryWrapper<AlbumInfo>()
				.eq(AlbumInfo::getUserId, userId)
				.eq(StringUtils.hasText(query.getStatus()), AlbumInfo::getStatus, query.getStatus())
				.like(StringUtils.hasText(query.getAlbumTitle()), AlbumInfo::getAlbumTitle, query.getAlbumTitle())
				.orderByDesc(AlbumInfo::getId);
		Page<AlbumInfo> albumPage = albumInfoMapper.selectPage(new Page<>(page, limit), queryWrapper);

		//2.转成页面需要的VO，统计数先给0，避免前端渲染出空值
		List<AlbumListVo> voList = albumPage.getRecords().stream().map(albumInfo -> {
			AlbumListVo albumListVo = new AlbumListVo();
			albumListVo.setAlbumId(albumInfo.getId());
			albumListVo.setAlbumTitle(albumInfo.getAlbumTitle());
			albumListVo.setCoverUrl(albumInfo.getCoverUrl());
			albumListVo.setIncludeTrackCount(albumInfo.getIncludeTrackCount());
			albumListVo.setIsFinished(albumInfo.getIsFinished());
			albumListVo.setStatus(albumInfo.getStatus());
			albumListVo.setPlayStatNum(0);
			albumListVo.setSubscribeStatNum(0);
			albumListVo.setBuyStatNum(0);
			albumListVo.setCommentStatNum(0);
			return albumListVo;
		}).collect(Collectors.toList());

		Page<AlbumListVo> result = new Page<>(albumPage.getCurrent(), albumPage.getSize(), albumPage.getTotal());
		result.setRecords(voList);
		if (CollectionUtils.isEmpty(voList)) {
			return result;
		}

		//3.一次性查出本页所有专辑的统计数据再回填，避免每张专辑查一次库
		Map<Long, AlbumListVo> albumListVoMap = voList.stream()
				.collect(Collectors.toMap(AlbumListVo::getAlbumId, Function.identity()));
		List<AlbumStat> albumStatList = albumStatMapper.selectList(new LambdaQueryWrapper<AlbumStat>()
				.in(AlbumStat::getAlbumId, albumListVoMap.keySet()));
		for (AlbumStat albumStat : albumStatList) {
			AlbumListVo albumListVo = albumListVoMap.get(albumStat.getAlbumId());
			if (albumListVo == null || albumStat.getStatType() == null) {
				continue;
			}
			switch (albumStat.getStatType()) {
				case SystemConstant.ALBUM_STAT_PLAY -> albumListVo.setPlayStatNum(albumStat.getStatNum());
				case SystemConstant.ALBUM_STAT_SUBSCRIBE -> albumListVo.setSubscribeStatNum(albumStat.getStatNum());
				case SystemConstant.ALBUM_STAT_BUY -> albumListVo.setBuyStatNum(albumStat.getStatNum());
				case SystemConstant.ALBUM_STAT_COMMENT -> albumListVo.setCommentStatNum(albumStat.getStatNum());
				default -> log.warn("未知的专辑统计类型：{}，专辑ID：{}", albumStat.getStatType(), albumStat.getAlbumId());
			}
		}
		return result;
	}

	/**
	 * 查询当前登录用户的全部专辑（下拉选择用）
	 * <p>
	 * 只查当前登录用户的专辑，新增声音时用它填「所属专辑」下拉框
	 *
	 * @param userId 当前登录用户ID
	 * @return 专辑集合（只含ID与标题）
	 */
	@Override
	public List<AlbumInfo> findUserAllAlbumList(Long userId) {
		return albumInfoMapper.selectList(new LambdaQueryWrapper<AlbumInfo>()
				.select(AlbumInfo::getId, AlbumInfo::getAlbumTitle)
				.eq(AlbumInfo::getUserId, userId)
				.orderByDesc(AlbumInfo::getId));
	}

	/**
	 * 查询专辑详情（编辑回显用）
	 *
	 * @param userId  当前登录用户ID
	 * @param albumId 专辑ID
	 * @return 专辑详情（含专辑标签集合）
	 */
	@Override
	public AlbumInfo getAlbumInfo(Long userId, Long albumId) {
		//1.查专辑，并且必须是当前登录用户自己的（别人专辑的编辑入口本就不存在）
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		if (albumInfo == null || !albumInfo.getUserId().equals(userId)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑不存在或无权查看");
		}

		//2.回填专辑标签：编辑页要用 attributeId/valueId 回显选中状态
		List<AlbumAttributeValue> attributeValueList = albumAttributeValueMapper.selectList(
				new LambdaQueryWrapper<AlbumAttributeValue>()
						.eq(AlbumAttributeValue::getAlbumId, albumId)
						.orderByAsc(AlbumAttributeValue::getAttributeId));
		albumInfo.setAlbumAttributeValueVoList(attributeValueList);

		//3.顺手把标签名、标签值名也填上，编辑页的标签chips能直接展示名字
		if (!CollectionUtils.isEmpty(attributeValueList)) {
			List<Long> attributeIds = attributeValueList.stream().map(AlbumAttributeValue::getAttributeId)
					.distinct().collect(Collectors.toList());
			List<Long> valueIds = attributeValueList.stream().map(AlbumAttributeValue::getValueId)
					.distinct().collect(Collectors.toList());

			Map<Long, String> attributeNameMap = baseAttributeMapper.selectList(
							new LambdaQueryWrapper<BaseAttribute>().in(BaseAttribute::getId, attributeIds))
					.stream().collect(Collectors.toMap(BaseAttribute::getId, BaseAttribute::getAttributeName));
			Map<Long, String> valueNameMap = baseAttributeValueMapper.selectList(
							new LambdaQueryWrapper<BaseAttributeValue>().in(BaseAttributeValue::getId, valueIds))
					.stream().collect(Collectors.toMap(BaseAttributeValue::getId, BaseAttributeValue::getValueName));

			attributeValueList.forEach(attributeValue -> {
				attributeValue.setAttributeName(attributeNameMap.get(attributeValue.getAttributeId()));
				attributeValue.setValueName(valueNameMap.get(attributeValue.getValueId()));
			});
		}
		return albumInfo;
	}

	/**
	 * 删除专辑
	 * <p>
	 * 逻辑删除（@TableLogic 走 is_deleted 更新，不做物理删除），
	 * 同时把专辑标签关系、专辑统计数据一起删掉，避免留下孤儿记录
	 *
	 * @param userId  当前登录用户ID
	 * @param albumId 专辑ID
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeAlbumInfo(Long userId, Long albumId) {
		//1.校验：专辑必须存在，且属于当前用户
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		if (albumInfo == null || !albumInfo.getUserId().equals(userId)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑不存在或无权删除");
		}

		//2.删除专辑
		albumInfoMapper.deleteById(albumId);

		//3.连带删除专辑标签关系与统计数据
		albumAttributeValueMapper.delete(new LambdaQueryWrapper<AlbumAttributeValue>()
				.eq(AlbumAttributeValue::getAlbumId, albumId));
		albumStatMapper.delete(new LambdaQueryWrapper<AlbumStat>()
				.eq(AlbumStat::getAlbumId, albumId));
		log.info("删除专辑成功，专辑ID：{}，操作人：{}", albumId, userId);

		//4.通知搜索模块删除索引：专辑都删了，搜索页不能再搜出来
		albumIndexMqSender.sendLowerAlbum(albumId);
	}


	/**
	 * 根据专辑ID查询专辑（服务内部调用）
	 * <p>
	 * 不做归属校验，专辑不存在时返回 null，由调用方（搜索模块）决定是删索引还是提示不存在
	 *
	 * @param albumId 专辑ID
	 * @return 专辑信息（含标签集合，标签只带 attributeId 与 valueId）
	 */
	@Override
	public AlbumInfo getAlbumInfoById(Long albumId) {
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		if (albumInfo == null) {
			return null;
		}
		//回填标签：搜索模块构建索引时要把标签写进 nested 字段，用于「按标签筛选」
		albumInfo.setAlbumAttributeValueVoList(albumAttributeValueMapper.selectList(
				new LambdaQueryWrapper<AlbumAttributeValue>()
						.select(AlbumAttributeValue::getAlbumId, AlbumAttributeValue::getAttributeId, AlbumAttributeValue::getValueId)
						.eq(AlbumAttributeValue::getAlbumId, albumId)
						.orderByAsc(AlbumAttributeValue::getAttributeId)));
		return albumInfo;
	}


	/**
	 * 查询专辑统计信息（服务内部调用）
	 *
	 * @param albumId 专辑ID
	 * @return 专辑统计信息，四项统计都查不到时各项为 0
	 */
	@Override
	public AlbumStatVo getAlbumStatVo(Long albumId) {
		AlbumStatVo albumStatVo = new AlbumStatVo();
		albumStatVo.setAlbumId(albumId);
		albumStatVo.setPlayStatNum(0);
		albumStatVo.setSubscribeStatNum(0);
		albumStatVo.setBuyStatNum(0);
		albumStatVo.setCommentStatNum(0);

		List<AlbumStat> albumStatList = albumStatMapper.selectList(new LambdaQueryWrapper<AlbumStat>()
				.eq(AlbumStat::getAlbumId, albumId));
		for (AlbumStat albumStat : albumStatList) {
			if (albumStat.getStatType() == null) {
				continue;
			}
			int statNum = albumStat.getStatNum() == null ? 0 : albumStat.getStatNum();
			switch (albumStat.getStatType()) {
				case SystemConstant.ALBUM_STAT_PLAY -> albumStatVo.setPlayStatNum(statNum);
				case SystemConstant.ALBUM_STAT_SUBSCRIBE -> albumStatVo.setSubscribeStatNum(statNum);
				case SystemConstant.ALBUM_STAT_BUY -> albumStatVo.setBuyStatNum(statNum);
				case SystemConstant.ALBUM_STAT_COMMENT -> albumStatVo.setCommentStatNum(statNum);
				default -> log.warn("未知的专辑统计类型：{}，专辑ID：{}", albumStat.getStatType(), albumId);
			}
		}
		return albumStatVo;
	}


	/**
	 * 查询全部专辑ID（服务内部调用）
	 *
	 * @return 专辑ID集合，按ID升序
	 */
	@Override
	public List<Long> findAllAlbumIds() {
		return albumInfoMapper.selectList(new LambdaQueryWrapper<AlbumInfo>()
						.select(AlbumInfo::getId)
						.orderByAsc(AlbumInfo::getId))
				.stream().map(AlbumInfo::getId).collect(Collectors.toList());
	}


	/**
	 * 根据专辑ID集合批量查询专辑（服务内部调用）
	 * <p>
	 * 入参可能来自用户填的一串 ID，先去重再去查，避免 in 条件里堆重复值；
	 * 查不到的那几个直接不在结果里，调用方按「缺了就是没了」处理
	 *
	 * @param albumIdList 专辑ID集合
	 * @return 专辑集合
	 */
	@Override
	public List<AlbumInfo> findAlbumInfoByIds(List<Long> albumIdList) {
		if (CollectionUtils.isEmpty(albumIdList)) {
			return new ArrayList<>();
		}
		List<Long> distinctIds = albumIdList.stream()
				.filter(Objects::nonNull)
				.distinct()
				.collect(Collectors.toList());
		if (CollectionUtils.isEmpty(distinctIds)) {
			return new ArrayList<>();
		}
		return albumInfoMapper.selectList(new LambdaQueryWrapper<AlbumInfo>()
				.in(AlbumInfo::getId, distinctIds));
	}


	/**
	 * 更新专辑统计信息（MQ 消费者调用）
	 * <p>
	 * 播放量、订阅量、购买量、评论数的变更都走这一个入口：
	 * 先按「专辑ID + 统计类型」原子累加，影响行数为 0 说明这一行不存在（老数据或初始化遗漏），
	 * 再补插一条，避免统计凭空丢失；
	 * 最后通知搜索模块刷新索引里的统计值与热度，保证排行榜、综合排序用的是最新数字
	 *
	 * @param albumStatMqVo 统计变更消息（专辑ID、统计类型、增量）
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateAlbumStat(AlbumStatMqVo albumStatMqVo) {
		//1.校验：专辑ID与统计类型缺一不可，增量为 0 时无需更新
		if (albumStatMqVo == null || albumStatMqVo.getAlbumId() == null
				|| !StringUtils.hasText(albumStatMqVo.getStatType())) {
			log.warn("专辑统计消息内容不完整，已忽略：{}", albumStatMqVo);
			return;
		}
		int count = albumStatMqVo.getCount() == null ? 0 : albumStatMqVo.getCount();
		if (count == 0) {
			return;
		}

		//2.原子累加：并发下由数据库保证计数不丢
		int rows = albumStatMapper.increaseStatNum(albumStatMqVo.getAlbumId(), albumStatMqVo.getStatType(), count);
		if (rows > 0) {
			log.info("专辑统计更新成功，专辑ID：{}，统计类型：{}，增量：{}",
					albumStatMqVo.getAlbumId(), albumStatMqVo.getStatType(), count);
		} else {
			//3.统计行不存在时补插一条：直接按增量作为初始值
			AlbumStat albumStat = new AlbumStat();
			albumStat.setAlbumId(albumStatMqVo.getAlbumId());
			albumStat.setStatType(albumStatMqVo.getStatType());
			albumStat.setStatNum(count);
			albumStatMapper.insert(albumStat);
			log.warn("专辑统计行缺失，已补插，专辑ID：{}，统计类型：{}，初始值：{}",
					albumStatMqVo.getAlbumId(), albumStatMqVo.getStatType(), count);
		}

		//4.通知搜索模块把索引里的统计值与热度刷新一遍
		//  统计是榜单和综合排序的依据，只写库不刷索引的话，榜单会一直停在建索引那一刻的快照
		albumIndexMqSender.sendAlbumStatToEs(albumStatMqVo.getAlbumId());
	}


	/**
	 * 编辑专辑
	 * <p>
	 * 只更新表单里能改的字段；null 字段 MyBatis-Plus 会忽略，不会把库里原有值覆盖成空。
	 * 专辑标签按提交内容整体替换（校验规则与保存专辑完全一致，直接复用）
	 *
	 * @param userId      当前登录用户ID
	 * @param albumId     专辑ID
	 * @param albumInfoVo 专辑信息
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public void updateAlbumInfo(Long userId, Long albumId, AlbumInfoVo albumInfoVo) {
		//1.校验：专辑必须存在，且属于当前用户
		AlbumInfo existAlbum = albumInfoMapper.selectById(albumId);
		if (existAlbum == null || !existAlbum.getUserId().equals(userId)) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑不存在或无权修改");
		}

		//2.校验分类与付费规则，与保存专辑同一套规则
		BaseCategoryView categoryView = baseCategoryViewMapper.selectOne(new LambdaQueryWrapper<BaseCategoryView>()
				.eq(BaseCategoryView::getCategory3Id, albumInfoVo.getCategory3Id())
				.last("limit 1"));
		if (categoryView == null) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "所选专辑分类不存在，请重新选择");
		}
		this.checkPayRule(albumInfoVo);

		//3.更新专辑：只塞允许修改的字段（userId、includeTrackCount、status 等一律不动）
		AlbumInfo albumInfo = new AlbumInfo();
		albumInfo.setId(albumId);
		albumInfo.setAlbumTitle(albumInfoVo.getAlbumTitle().trim());
		albumInfo.setCategory3Id(albumInfoVo.getCategory3Id());
		albumInfo.setAlbumIntro(albumInfoVo.getAlbumIntro());
		albumInfo.setCoverUrl(albumInfoVo.getCoverUrl());
		albumInfo.setAlbumRichIntro(albumInfoVo.getAlbumRichIntro());
		albumInfo.setEstimatedTrackCount(albumInfoVo.getEstimatedTrackCount());
		albumInfo.setPayType(albumInfoVo.getPayType());
		albumInfo.setPriceType(albumInfoVo.getPriceType());
		albumInfo.setPrice(albumInfoVo.getPrice());
		albumInfo.setDiscount(albumInfoVo.getDiscount());
		albumInfo.setVipDiscount(albumInfoVo.getVipDiscount());
		albumInfo.setTracksForFree(albumInfoVo.getTracksForFree());
		albumInfo.setSecondsForFree(albumInfoVo.getSecondsForFree());
		albumInfo.setBuyNotes(albumInfoVo.getBuyNotes());
		albumInfo.setSellingPoint(albumInfoVo.getSellingPoint());
		albumInfo.setIsOpen(albumInfoVo.getIsOpen());
		albumInfoMapper.updateById(albumInfo);

		//4.替换专辑标签：先清掉旧的，再按本次提交的内容写入
		albumAttributeValueMapper.delete(new LambdaQueryWrapper<AlbumAttributeValue>()
				.eq(AlbumAttributeValue::getAlbumId, albumId));
		this.saveAlbumAttributeValue(albumId, categoryView.getCategory1Id(), albumInfoVo.getAlbumAttributeValueVoList());
		log.info("编辑专辑成功，专辑ID：{}，操作人：{}", albumId, userId);

		//5.通知搜索模块重建索引：标题、分类、标签都可能变了，索引里的数据要跟着换
		albumIndexMqSender.sendUpperAlbum(albumId);
	}

	/**
	 * 校验付费相关参数
	 *
	 * @param albumInfoVo 专辑信息
	 */
	private void checkPayRule(AlbumInfoVo albumInfoVo) {
		String payType = albumInfoVo.getPayType();
		//免费专辑不限制价格
		if (SystemConstant.ALBUM_PAY_TYPE_FREE.equals(payType)) {
			return;
		}
		if (!StringUtils.hasText(albumInfoVo.getPriceType()) || !PRICE_TYPES.contains(albumInfoVo.getPriceType())) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "请选择专辑价格类型");
		}
		BigDecimal price = albumInfoVo.getPrice();
		if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "付费专辑必须填写大于0的价格");
		}
		this.checkDiscount(albumInfoVo.getDiscount(), "专辑折扣");
		this.checkDiscount(albumInfoVo.getVipDiscount(), "VIP折扣");
	}

	/**
	 * 校验折扣取值：为空按不打折处理，一旦填写就必须落在 0.1~9.9
	 *
	 * @param discount 折扣值
	 * @param fieldName 字段名称（用于错误提示）
	 */
	private void checkDiscount(BigDecimal discount, String fieldName) {
		if (discount == null || discount.compareTo(DISCOUNT_NONE) == 0) {
			return;
		}
		if (discount.compareTo(DISCOUNT_MIN) < 0 || discount.compareTo(DISCOUNT_MAX) > 0) {
			throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), fieldName + "取值范围为0.1~9.9，不打折请填-1");
		}
	}

	/**
	 * 组装专辑实体：客户端提交的字段做必要兜底，服务端托管的字段一律自己赋值，避免被请求参数篡改
	 *
	 * @param userId 当前登录用户ID
	 * @param albumInfoVo 专辑信息
	 * @return 专辑实体
	 */
	private AlbumInfo buildAlbumInfo(Long userId, AlbumInfoVo albumInfoVo) {
		AlbumInfo albumInfo = new AlbumInfo();
		//客户端提交的字段
		albumInfo.setAlbumTitle(albumInfoVo.getAlbumTitle().trim());
		albumInfo.setCategory3Id(albumInfoVo.getCategory3Id());
		albumInfo.setAlbumIntro(albumInfoVo.getAlbumIntro());
		albumInfo.setCoverUrl(albumInfoVo.getCoverUrl());
		albumInfo.setAlbumRichIntro(albumInfoVo.getAlbumRichIntro());
		albumInfo.setEstimatedTrackCount(albumInfoVo.getEstimatedTrackCount() == null ? 0 : albumInfoVo.getEstimatedTrackCount());
		albumInfo.setPayType(albumInfoVo.getPayType());
		albumInfo.setPriceType(albumInfoVo.getPriceType());
		albumInfo.setPrice(albumInfoVo.getPrice() == null ? BigDecimal.ZERO : albumInfoVo.getPrice());
		albumInfo.setDiscount(albumInfoVo.getDiscount() == null ? DISCOUNT_NONE : albumInfoVo.getDiscount());
		albumInfo.setVipDiscount(albumInfoVo.getVipDiscount() == null ? DISCOUNT_NONE : albumInfoVo.getVipDiscount());
		albumInfo.setTracksForFree(albumInfoVo.getTracksForFree());
		albumInfo.setSecondsForFree(albumInfoVo.getSecondsForFree());
		albumInfo.setBuyNotes(albumInfoVo.getBuyNotes());
		albumInfo.setSellingPoint(albumInfoVo.getSellingPoint());
		albumInfo.setIsOpen(StringUtils.hasText(albumInfoVo.getIsOpen()) ? albumInfoVo.getIsOpen() : "1");

		//服务端托管、不接受客户端传入的字段
		albumInfo.setUserId(userId);
		albumInfo.setIncludeTrackCount(0);
		albumInfo.setIsFinished("0");
		//内容审核机制暂未实现，先置为审核通过，后续接入审核后改为待审核
		albumInfo.setStatus(SystemConstant.ALBUM_STATUS_PASS);
		return albumInfo;
	}

	/**
	 * 保存专辑标签关系
	 * <p>
	 * 标签值直接决定后续检索结果，因此这里的校验不能只靠前端：
	 * 1.同一个标签只能选一个值；2.标签值必须真的属于该标签；3.标签必须属于专辑所选分类下
	 *
	 * @param albumId          专辑ID
	 * @param category1Id      专辑三级分类所属的一级分类ID
	 * @param attributeValueVoList 提交的标签集合
	 */
	private void saveAlbumAttributeValue(Long albumId, Long category1Id, List<AlbumAttributeValueVo> attributeValueVoList) {
		if (CollectionUtils.isEmpty(attributeValueVoList)) {
			return;
		}
		//1.按标签ID去重，同一个标签提交多个值视为非法请求
		Map<Long, AlbumAttributeValueVo> attributeMap = new LinkedHashMap<>();
		for (AlbumAttributeValueVo attributeValueVo : attributeValueVoList) {
			if (attributeMap.put(attributeValueVo.getAttributeId(), attributeValueVo) != null) {
				throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "同一个专辑标签只能选择一个标签值");
			}
		}

		//2.批量查出标签值与标签，逐一校验归属关系
		Map<Long, BaseAttributeValue> valueMap = baseAttributeValueMapper.selectList(
						new LambdaQueryWrapper<BaseAttributeValue>().in(BaseAttributeValue::getId, attributeMap.values().stream()
								.map(AlbumAttributeValueVo::getValueId).collect(Collectors.toList())))
				.stream().collect(Collectors.toMap(BaseAttributeValue::getId, Function.identity()));
		Map<Long, BaseAttribute> baseAttributeMap = baseAttributeMapper.selectList(
						new LambdaQueryWrapper<BaseAttribute>().in(BaseAttribute::getId, attributeMap.keySet()))
				.stream().collect(Collectors.toMap(BaseAttribute::getId, Function.identity()));

		//3.保存标签关系
		for (Map.Entry<Long, AlbumAttributeValueVo> entry : attributeMap.entrySet()) {
			Long attributeId = entry.getKey();
			Long valueId = entry.getValue().getValueId();

			BaseAttributeValue attributeValue = valueMap.get(valueId);
			if (attributeValue == null || !attributeId.equals(attributeValue.getAttributeId())) {
				throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑标签值不存在或与标签不匹配");
			}
			BaseAttribute baseAttribute = baseAttributeMap.get(attributeId);
			if (baseAttribute == null || !category1Id.equals(baseAttribute.getCategory1Id())) {
				throw new GuiguException(ResultCodeEnum.ILLEGAL_REQUEST.getCode(), "专辑标签不属于当前所选分类");
			}

			AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
			albumAttributeValue.setAlbumId(albumId);
			albumAttributeValue.setAttributeId(attributeId);
			albumAttributeValue.setValueId(valueId);
			albumAttributeValueMapper.insert(albumAttributeValue);
		}
	}

	/**
	 * 初始化一条专辑统计记录
	 *
	 * @param albumId  专辑ID
	 * @param statType 统计类型：0401-播放量 0402-订阅量 0403-购买量 0404-评论数
	 */
	private void saveAlbumStat(Long albumId, String statType) {
		AlbumStat albumStat = new AlbumStat();
		albumStat.setAlbumId(albumId);
		albumStat.setStatType(statType);
		albumStat.setStatNum(0);
		albumStatMapper.insert(albumStat);
	}

}
