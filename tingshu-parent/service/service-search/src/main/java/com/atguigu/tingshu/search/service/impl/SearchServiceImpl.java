package com.atguigu.tingshu.search.service.impl;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.DoubleTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.NamedValue;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.PinYinUtils;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.search.ChannelDataVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.data.elasticsearch.core.suggest.response.Suggest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 专辑检索：索引维护 + 检索 + 联想 + 频道页 + 排行榜
 * <p>
 * 索引库 albuminfo 的字段类型（text/keyword/nested）由 AlbumInfoIndex 上的注解决定，
 * 检索时的条件与排序字段必须和索引里的字段名、字段类型对得上，否则查不出数据
 */
@Slf4j
@Service
@SuppressWarnings({"all"})
public class SearchServiceImpl implements SearchService {

	/** 索引库字段名 */
	private static final String FIELD_ID = "id";
	private static final String FIELD_ALBUM_TITLE = "albumTitle";
	private static final String FIELD_ALBUM_INTRO = "albumIntro";
	private static final String FIELD_ANNOUNCER_NAME = "announcerName";
	private static final String FIELD_CATEGORY1_ID = "category1Id";
	private static final String FIELD_CATEGORY2_ID = "category2Id";
	private static final String FIELD_CATEGORY3_ID = "category3Id";
	private static final String FIELD_HOT_SCORE = "hotScore";
	private static final String FIELD_PLAY_STAT_NUM = "playStatNum";
	private static final String FIELD_SUBSCRIBE_STAT_NUM = "subscribeStatNum";
	private static final String FIELD_BUY_STAT_NUM = "buyStatNum";
	private static final String FIELD_COMMENT_STAT_NUM = "commentStatNum";
	private static final String FIELD_CREATE_TIME = "createTime";
	private static final String FIELD_ATTRIBUTE_VALUE_LIST = "attributeValueIndexList";
	private static final String FIELD_ATTRIBUTE_ID = FIELD_ATTRIBUTE_VALUE_LIST + ".attributeId";
	private static final String FIELD_VALUE_ID = FIELD_ATTRIBUTE_VALUE_LIST + ".valueId";

	/** 搜索建议索引库字段名 */
	private static final String SUGGEST_FIELD_KEYWORD = "keyword";
	private static final String SUGGEST_FIELD_PINYIN = "keywordPinyin";
	private static final String SUGGEST_FIELD_SEQUENCE = "keywordSequence";

	/** 聚合名称：按三级分类分桶、桶内热度均值、桶内专辑明细 */
	private static final String AGG_CATEGORY3 = "category3Agg";
	private static final String AGG_HOT_SCORE_AVG = "hotScoreAvg";
	private static final String AGG_TOP_ALBUMS = "topAlbums";

	/** 关键字检索时专辑标题的权重：标题命中比简介命中更贴题 */
	private static final String TITLE_BOOST = "3";

	/** 热度分值权重：热度 = 播放量*0.1 + 订阅量*0.2 + 购买量*0.3 + 评论数*0.4 */
	private static final double WEIGHT_PLAY = 0.1;
	private static final double WEIGHT_SUBSCRIBE = 0.2;
	private static final double WEIGHT_BUY = 0.3;
	private static final double WEIGHT_COMMENT = 0.4;

	/** 排序参数里的排序方式：1-综合排序（热度） 2-播放量 3-发布时间 */
	private static final String ORDER_TYPE_HOT_SCORE = "1";
	private static final String ORDER_TYPE_PLAY_STAT_NUM = "2";
	private static final String ORDER_TYPE_CREATE_TIME = "3";

	/** 频道页：最多展示多少个三级分类分段，每个分段下展示多少张专辑 */
	private static final int CHANNEL_SECTION_SIZE = 20;
	private static final int CHANNEL_ALBUM_SIZE = 4;

	/** 排行榜展示条数 */
	private static final int RANKING_SIZE = 10;

	/** 搜索建议返回条数 */
	private static final int SUGGEST_SIZE = 10;

	/** 单页最大条数：防止前端传个超大分页把 ES 拖垮 */
	private static final int MAX_PAGE_SIZE = 100;

	/** 布隆过滤器参数：预计存放的专辑数量与误判率 */
	private static final long BLOOM_FILTER_EXPECTED_INSERTIONS = 100000;
	private static final double BLOOM_FILTER_FALSE_PROBABILITY = 0.01;

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private AlbumFeignClient albumFeignClient;

	@Autowired
	private UserFeignClient userFeignClient;

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private RedisTemplate redisTemplate;


	/**
	 * 专辑上架（建立索引）
	 * <p>
	 * 数据分散在专辑库与用户库，这里通过 Feign 把一份「检索用」的宽表拼出来写进 ES：
	 * 专辑本体 + 主播名称 + 一二级分类 + 统计信息 + 专辑标签
	 *
	 * @param albumId 专辑ID
	 */
	@Override
	public void upperAlbum(Long albumId) {
		//1.查询专辑本体（专辑被删掉了就顺手把历史索引清掉，保证索引与库一致）
		AlbumInfo albumInfo = this.getData(albumFeignClient.getAlbumInfoById(albumId));
		if (albumInfo == null) {
			log.warn("专辑不存在，跳过建立索引并清理历史索引，专辑ID：{}", albumId);
			this.lowerAlbum(albumId);
			return;
		}

		//2.组装索引数据
		AlbumInfoIndex albumInfoIndex = new AlbumInfoIndex();
		BeanUtils.copyProperties(albumInfo, albumInfoIndex);

		//2.1 主播名称：索引里是 keyword（不分词），检索时按精确值匹配主播
		UserInfoVo announcer = this.getData(userFeignClient.getUserInfo(albumInfo.getUserId()));
		albumInfoIndex.setAnnouncerName(announcer == null ? null : announcer.getNickname());

		//2.2 一、二级分类：专辑表里只有三级分类，去分类视图补齐（检索要支持按一、二级分类筛选）
		BaseCategoryView categoryView = this.getData(albumFeignClient.getBaseCategoryView(albumInfo.getCategory3Id()));
		if (categoryView != null) {
			albumInfoIndex.setCategory1Id(categoryView.getCategory1Id());
			albumInfoIndex.setCategory2Id(categoryView.getCategory2Id());
		}

		//2.3 统计信息与热度：热度是综合排序的依据
		AlbumStatVo albumStatVo = this.getData(albumFeignClient.getAlbumStatVo(albumId));
		if (albumStatVo != null) {
			albumInfoIndex.setPlayStatNum(albumStatVo.getPlayStatNum());
			albumInfoIndex.setSubscribeStatNum(albumStatVo.getSubscribeStatNum());
			albumInfoIndex.setBuyStatNum(albumStatVo.getBuyStatNum());
			albumInfoIndex.setCommentStatNum(albumStatVo.getCommentStatNum());
		}
		albumInfoIndex.setHotScore(this.calculateHotScore(albumInfoIndex));

		//2.4 专辑标签：检索要按「标签id+标签值id」筛选，写进 nested 字段
		albumInfoIndex.setAttributeValueIndexList(this.buildAttributeValueIndexList(albumInfo.getAlbumAttributeValueVoList()));

		//3.写入索引库：文档id就是专辑id，新增与编辑共用一套逻辑，重复执行是覆盖
		elasticsearchOperations.save(albumInfoIndex);

		//4.写入搜索建议库：中文、全拼、拼音首字母三种输入都能联想
		this.saveSuggestIndex(albumInfo);

		//5.加入布隆过滤器：专辑详情页用它挡掉「一定不存在」的请求
		this.getAlbumBloomFilter().add(albumId);

		log.info("专辑建立索引成功，专辑ID：{}，标题：{}，热度：{}", albumId, albumInfo.getAlbumTitle(), albumInfoIndex.getHotScore());
	}


	/**
	 * 专辑下架（删除索引）
	 * <p>
	 * 索引删掉之后还得把专辑详情的缓存清掉，否则专辑已经下架、详情页却还能从缓存里打开
	 *
	 * @param albumId 专辑ID
	 */
	@Override
	public void lowerAlbum(Long albumId) {
		this.deleteIndexDocument(String.valueOf(albumId), AlbumInfoIndex.class);
		this.deleteIndexDocument(String.valueOf(albumId), SuggestIndex.class);
		try {
			redisTemplate.delete(RedisConstant.ALBUM_INFO_PREFIX + albumId);
		} catch (Exception e) {
			log.warn("清理专辑详情缓存失败，专辑ID：{}", albumId, e);
		}
		log.info("专辑索引删除完成，专辑ID：{}", albumId);
	}


	/**
	 * 刷新索引里的专辑统计与热度
	 * <p>
	 * 播放上报很密集，所以这里只改统计字段，不整条重建索引：
	 * 重建会连带给搜索建议库重写一遍，也会把标题、分类、标签一起覆盖，
	 * 那些字段这次根本没变，多写一次就多一次写错的机会。
	 * <p>
	 * 每次取的是库里的最新总量而不是增量，所以即使中间漏掉几条消息，
	 * 后面任意一条消息都能把数字纠回来，不存在累积偏差。
	 *
	 * @param albumId 专辑ID
	 */
	@Override
	public void updateAlbumStatIndex(Long albumId) {
		if (albumId == null) {
			return;
		}
		//1.索引里没有这张专辑，说明还没建过索引（建索引时会带上最新统计），这里不用管
		if (!elasticsearchOperations.exists(String.valueOf(albumId), AlbumInfoIndex.class)) {
			log.info("专辑尚未建立索引，跳过统计刷新，专辑ID：{}", albumId);
			return;
		}

		//2.取最新统计
		AlbumStatVo albumStatVo = this.getData(albumFeignClient.getAlbumStatVo(albumId));
		if (albumStatVo == null) {
			log.warn("查询专辑统计为空，跳过统计刷新，专辑ID：{}", albumId);
			return;
		}

		//3.先算出热度：热度是统计值的组合，字段更新时必须一起算，否则排序用的还是旧热度
		AlbumInfoIndex statIndex = new AlbumInfoIndex();
		statIndex.setPlayStatNum(this.toInt(albumStatVo.getPlayStatNum()));
		statIndex.setSubscribeStatNum(this.toInt(albumStatVo.getSubscribeStatNum()));
		statIndex.setBuyStatNum(this.toInt(albumStatVo.getBuyStatNum()));
		statIndex.setCommentStatNum(this.toInt(albumStatVo.getCommentStatNum()));
		statIndex.setHotScore(this.calculateHotScore(statIndex));

		//4.局部更新：文档id就是专辑id，只提交这几个字段，其余字段保持原样
		Map<String, Object> statFields = new LinkedHashMap<>();
		statFields.put(FIELD_PLAY_STAT_NUM, statIndex.getPlayStatNum());
		statFields.put(FIELD_SUBSCRIBE_STAT_NUM, statIndex.getSubscribeStatNum());
		statFields.put(FIELD_BUY_STAT_NUM, statIndex.getBuyStatNum());
		statFields.put(FIELD_COMMENT_STAT_NUM, statIndex.getCommentStatNum());
		statFields.put(FIELD_HOT_SCORE, statIndex.getHotScore());

		UpdateQuery updateQuery = UpdateQuery.builder(String.valueOf(albumId))
				.withDocument(Document.from(statFields))
				//同一张专辑被多人同时听时会有并发写同一个文档的情况，冲突交给 ES 自己重试
				.withRetryOnConflict(3)
				.build();
		elasticsearchOperations.update(updateQuery, elasticsearchOperations.getIndexCoordinatesFor(AlbumInfoIndex.class));
		log.info("专辑索引统计已刷新，专辑ID：{}，播放量：{}，订阅量：{}，购买量：{}，评论数：{}，热度：{}",
				albumId, statIndex.getPlayStatNum(), statIndex.getSubscribeStatNum(),
				statIndex.getBuyStatNum(), statIndex.getCommentStatNum(), statIndex.getHotScore());
	}


	/**
	 * 检索专辑
	 * <p>
	 * 关键字走全文检索（标题权重高于简介）、分类与专辑标签走过滤，
	 * 排序支持综合排序（热度）、播放量、发布时间，结果分页返回
	 *
	 * @param albumIndexQuery 检索条件（含分页信息）
	 * @return 检索结果
	 */
	@Override
	public AlbumSearchResponseVo searchAlbum(AlbumIndexQuery albumIndexQuery) {
		AlbumIndexQuery query = albumIndexQuery == null ? new AlbumIndexQuery() : albumIndexQuery;

		//1.分页参数兜底：页码从1开始，每页条数限制上限
		int pageNo = query.getPageNo() == null || query.getPageNo() < 1 ? 1 : query.getPageNo();
		int pageSize = query.getPageSize() == null || query.getPageSize() < 1
				? 10 : Math.min(query.getPageSize(), MAX_PAGE_SIZE);

		//2.组装检索条件、排序与分页
		NativeQuery nativeQuery = NativeQuery.builder()
				.withQuery(this.buildQuery(query))
				.withSort(this.buildSort(query.getOrder()))
				.withPageable(PageRequest.of(pageNo - 1, pageSize))
				.build();

		//3.执行检索
		SearchHits<AlbumInfoIndex> searchHits = elasticsearchOperations.search(nativeQuery, AlbumInfoIndex.class);
		long total = searchHits.getTotalHits();

		//4.组装返回结果
		AlbumSearchResponseVo responseVo = new AlbumSearchResponseVo();
		responseVo.setList(searchHits.getSearchHits().stream()
				.map(searchHit -> this.toAlbumInfoIndexVo(searchHit.getContent()))
				.collect(Collectors.toList()));
		responseVo.setTotal(total);
		responseVo.setPageNo(pageNo);
		responseVo.setPageSize(pageSize);
		responseVo.setTotalPages(total % pageSize == 0 ? total / pageSize : total / pageSize + 1);
		log.info("专辑检索完成，关键字：{}，分类：{}，标签：{}，排序：{}，命中：{}",
				query.getKeyword(), query.getCategory3Id(), query.getAttributeList(), query.getOrder(), total);
		return responseVo;
	}


	/**
	 * 搜索建议（输入联想）
	 *
	 * @param keyword 用户输入的关键字
	 * @return 建议词集合
	 */
	@Override
	public List<String> completeSuggest(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return new ArrayList<>();
		}
		String text = keyword.trim();

		//1.三个 completion 字段各查一次：中文、全拼、拼音首字母，用户输入哪种都能联想出来
		Suggester suggester = Suggester.of(suggesterBuilder -> suggesterBuilder
				.text(text)
				.suggesters(SUGGEST_FIELD_KEYWORD, field -> field.completion(completion -> completion
						.field(SUGGEST_FIELD_KEYWORD).size(SUGGEST_SIZE).skipDuplicates(true)))
				.suggesters(SUGGEST_FIELD_PINYIN, field -> field.completion(completion -> completion
						.field(SUGGEST_FIELD_PINYIN).size(SUGGEST_SIZE).skipDuplicates(true)))
				.suggesters(SUGGEST_FIELD_SEQUENCE, field -> field.completion(completion -> completion
						.field(SUGGEST_FIELD_SEQUENCE).size(SUGGEST_SIZE).skipDuplicates(true))));
		NativeQuery nativeQuery = NativeQuery.builder()
				.withSuggester(suggester)
				.withMaxResults(0)
				.build();
		SearchHits<SuggestIndex> searchHits = elasticsearchOperations.search(nativeQuery, SuggestIndex.class);

		//2.解析建议结果：ES 把三组建议分开返回，这里合成一个去重后的集合
		LinkedHashSet<String> suggestions = new LinkedHashSet<>();
		Suggest suggest = searchHits.getSuggest();
		if (suggest != null) {
			for (Suggest.Suggestion<? extends Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option>> suggestion : suggest.getSuggestions()) {
				for (Suggest.Suggestion.Entry<? extends Suggest.Suggestion.Entry.Option> entry : suggestion.getEntries()) {
					for (Suggest.Suggestion.Entry.Option option : entry.getOptions()) {
						if (StringUtils.hasText(option.getText())) {
							suggestions.add(option.getText());
						}
					}
				}
			}
		}
		return suggestions.stream().limit(SUGGEST_SIZE).collect(Collectors.toList());
	}


	/**
	 * 查询频道页数据（首页）
	 * <p>
	 * 一次聚合查询完成：按三级分类分桶，每个桶内按热度取前几张专辑，
	 * 避免「先查分类再逐个分类查专辑」的多次往返
	 *
	 * @param category1Id 一级分类ID
	 * @return 频道页分段数据
	 */
	@Override
	public List<ChannelDataVo> getChannelData(Long category1Id) {
		List<ChannelDataVo> channelDataVoList = new ArrayList<>();
		if (category1Id == null) {
			return channelDataVoList;
		}

		//1.取该一级分类下的三级分类，用来给每个分段补上分类名称
		List<BaseCategory3> category3List = this.getData(albumFeignClient.findTopBaseCategory3(category1Id));
		Map<Long, BaseCategory3> category3Map = CollectionUtils.isEmpty(category3List) ? new HashMap<>()
				: category3List.stream().collect(Collectors.toMap(BaseCategory3::getId, Function.identity(), (a, b) -> a));

		//2.聚合查询：按三级分类分桶（按桶内热度均值倒序），桶内用 top_hits 取热度最高的专辑
		NativeQuery nativeQuery = NativeQuery.builder()
				.withQuery(query -> query.term(term -> term.field(FIELD_CATEGORY1_ID).value(category1Id)))
				.withAggregation(AGG_CATEGORY3, Aggregation.of(aggregation -> aggregation
						.terms(terms -> terms.field(FIELD_CATEGORY3_ID)
								.size(CHANNEL_SECTION_SIZE)
								.order(NamedValue.of(AGG_HOT_SCORE_AVG, SortOrder.Desc)))
						.aggregations(AGG_HOT_SCORE_AVG, Aggregation.of(sub -> sub.avg(avg -> avg.field(FIELD_HOT_SCORE))))
						.aggregations(AGG_TOP_ALBUMS, Aggregation.of(sub -> sub.topHits(topHits -> topHits
								.size(CHANNEL_ALBUM_SIZE)
								.sort(List.of(SortOptions.of(sort -> sort.field(field -> field
										.field(FIELD_HOT_SCORE).order(SortOrder.Desc))))))))))
				.withMaxResults(0)
				.build();
		SearchHits<AlbumInfoIndex> searchHits = elasticsearchOperations.search(nativeQuery, AlbumInfoIndex.class);

		//3.解析聚合结果
		if (!(searchHits.getAggregations() instanceof ElasticsearchAggregations aggregations)) {
			return channelDataVoList;
		}
		ElasticsearchAggregation category3Aggregation = aggregations.get(AGG_CATEGORY3);
		if (category3Aggregation == null) {
			return channelDataVoList;
		}
		for (Map.Entry<Long, Map<String, Aggregate>> bucketEntry
				: this.toCategory3Buckets(category3Aggregation.aggregation().getAggregate()).entrySet()) {
			//3.1 分类信息：分类被删掉了这一段就没有名称可展示，直接跳过
			BaseCategory3 baseCategory3 = category3Map.get(bucketEntry.getKey());
			if (baseCategory3 == null) {
				continue;
			}

			//3.2 分段下的专辑：top_hits 返回的是 JSON，转成索引实体再转成页面需要的VO
			Aggregate topAlbums = bucketEntry.getValue().get(AGG_TOP_ALBUMS);
			if (topAlbums == null) {
				continue;
			}
			List<AlbumInfoIndexVo> albumInfoIndexVoList = topAlbums.topHits().hits().hits().stream()
					.map(Hit::source)
					.filter(Objects::nonNull)
					.map(this::toAlbumInfoIndexFromJson)
					.filter(Objects::nonNull)
					.map(this::toAlbumInfoIndexVo)
					.collect(Collectors.toList());
			if (CollectionUtils.isEmpty(albumInfoIndexVoList)) {
				continue;
			}

			ChannelDataVo channelDataVo = new ChannelDataVo();
			channelDataVo.setBaseCategory3(baseCategory3);
			channelDataVo.setList(albumInfoIndexVoList);
			channelDataVoList.add(channelDataVo);
		}
		return channelDataVoList;
	}


	/**
	 * 解析「按三级分类分桶」的聚合结果
	 * <p>
	 * 桶的key是数值类型，ES 可能返回 lterms（long）、dterms（double）或 sterms（string）三种形态，
	 * 三种都要兼容，否则会报「Cannot get 'Sterms' variant: current variant is 'Lterms'」
	 *
	 * @param category3Aggregate 聚合结果
	 * @return 三级分类ID -> 该桶的子聚合（保持 ES 返回的顺序）
	 */
	private Map<Long, Map<String, Aggregate>> toCategory3Buckets(Aggregate category3Aggregate) {
		Map<Long, Map<String, Aggregate>> buckets = new LinkedHashMap<>();
		if (category3Aggregate.isSterms()) {
			//字段被当成字符串聚合（例如keyword字段）
			for (StringTermsBucket bucket : category3Aggregate.sterms().buckets().array()) {
				Long category3Id = this.toLong(bucket.key());
				if (category3Id != null) {
					buckets.put(category3Id, bucket.aggregations());
				}
			}
		} else if (category3Aggregate.isLterms()) {
			//数值字段聚合的默认形态
			for (LongTermsBucket bucket : category3Aggregate.lterms().buckets().array()) {
				buckets.put(bucket.key(), bucket.aggregations());
			}
		} else if (category3Aggregate.isDterms()) {
			for (DoubleTermsBucket bucket : category3Aggregate.dterms().buckets().array()) {
				buckets.put((long) bucket.key(), bucket.aggregations());
			}
		} else {
			log.warn("按三级分类聚合返回了不支持的类型：{}", category3Aggregate._kind());
		}
		return buckets;
	}


	/**
	 * 查询排行榜
	 *
	 * @param category1Id 一级分类ID
	 * @param dimension   排序维度（热度、播放量、订阅量、购买量、评论数）
	 * @return 排行榜专辑集合
	 */
	@Override
	public List<AlbumInfoIndexVo> findRankingList(Long category1Id, String dimension) {
		if (category1Id == null) {
			return new ArrayList<>();
		}
		//1.排序维度是前端传过来的，做一次白名单校验，避免拿别的字段来排序
		String sortField = this.toRankingSortField(dimension);

		//2.取一级分类下该维度倒序的前若干张专辑
		NativeQuery nativeQuery = NativeQuery.builder()
				.withQuery(query -> query.term(term -> term.field(FIELD_CATEGORY1_ID).value(category1Id)))
				.withSort(List.of(SortOptions.of(sort -> sort.field(field -> field.field(sortField).order(SortOrder.Desc)))))
				.withPageable(PageRequest.of(0, RANKING_SIZE))
				.build();
		SearchHits<AlbumInfoIndex> searchHits = elasticsearchOperations.search(nativeQuery, AlbumInfoIndex.class);
		return searchHits.getSearchHits().stream()
				.map(searchHit -> this.toAlbumInfoIndexVo(searchHit.getContent()))
				.collect(Collectors.toList());
	}


	/**
	 * 全量初始化专辑索引
	 * <p>
	 * 一次性把库里所有专辑灌进索引库，用于索引库首次搭建或数据修复，专辑多时耗时较长
	 *
	 * @return 成功建立索引的专辑数量
	 */
	@Override
	public int initAlbumIndex() {
		List<Long> albumIdList = this.getData(albumFeignClient.findAllAlbumIds());
		if (CollectionUtils.isEmpty(albumIdList)) {
			log.warn("没有查询到需要建立索引的专辑");
			return 0;
		}
		log.info("开始初始化专辑索引，专辑总数：{}", albumIdList.size());
		int successCount = 0;
		for (Long albumId : albumIdList) {
			try {
				this.upperAlbum(albumId);
				successCount++;
			} catch (Exception e) {
				log.error("专辑建立索引失败，专辑ID：{}", albumId, e);
			}
			if (successCount > 0 && successCount % 100 == 0) {
				log.info("专辑索引初始化进度：{}/{}", successCount, albumIdList.size());
			}
		}
		log.info("专辑索引初始化完成，成功：{}/{}", successCount, albumIdList.size());
		return successCount;
	}


	/**
	 * 组装检索条件
	 * <p>
	 * 关键字：标题（权重3）与简介走全文检索，主播名称是 keyword 只能精确匹配，三者命中其一即可；
	 * 分类与专辑标签：都是精确值，放进 filter，不参与打分
	 *
	 * @param query 检索条件
	 * @return ES 查询条件
	 */
	private Query buildQuery(AlbumIndexQuery query) {
		BoolQuery.Builder boolQuery = new BoolQuery.Builder();

		//1.关键字
		if (StringUtils.hasText(query.getKeyword())) {
			String keyword = query.getKeyword().trim();
			boolQuery.minimumShouldMatch("1");
			//1.1 标题、简介：ik 分词后的全文检索，标题命中权重更高
			boolQuery.should(should -> should.multiMatch(multiMatch -> multiMatch
					.query(keyword)
					.fields(FIELD_ALBUM_TITLE + "^" + TITLE_BOOST, FIELD_ALBUM_INTRO)));
			//1.2 主播名称：keyword 类型不分词，按精确值匹配
			boolQuery.should(should -> should.term(term -> term.field(FIELD_ANNOUNCER_NAME).value(keyword)));
		}

		//2.分类：一、二、三级分类都是精确值过滤
		if (query.getCategory1Id() != null) {
			boolQuery.filter(filter -> filter.term(term -> term.field(FIELD_CATEGORY1_ID).value(query.getCategory1Id())));
		}
		if (query.getCategory2Id() != null) {
			boolQuery.filter(filter -> filter.term(term -> term.field(FIELD_CATEGORY2_ID).value(query.getCategory2Id())));
		}
		if (query.getCategory3Id() != null) {
			boolQuery.filter(filter -> filter.term(term -> term.field(FIELD_CATEGORY3_ID).value(query.getCategory3Id())));
		}

		//3.专辑标签：每项条件都是「标签id:标签值id」，用 nested 查询保证是同一个标签对象上的两个值
		if (!CollectionUtils.isEmpty(query.getAttributeList())) {
			for (String attribute : query.getAttributeList()) {
				long[] attributeValue = this.parseAttribute(attribute);
				if (attributeValue == null) {
					continue;
				}
				boolQuery.filter(filter -> filter.nested(nested -> nested
						.path(FIELD_ATTRIBUTE_VALUE_LIST)
						.query(nestedQuery -> nestedQuery.bool(nestedBool -> nestedBool
								.must(must -> must.term(term -> term.field(FIELD_ATTRIBUTE_ID).value(attributeValue[0])))
								.must(must -> must.term(term -> term.field(FIELD_VALUE_ID).value(attributeValue[1])))))));
			}
		}
		return boolQuery.build()._toQuery();
	}


	/**
	 * 组装排序条件
	 * <p>
	 * order 形如「1:desc」：冒号前是排序方式（1-综合排序 2-播放量 3-发布时间），冒号后是升降序，
	 * 最后一个排序字段固定为专辑ID倒序，保证排序值相同的专辑顺序稳定（翻页不会重复或漏数据）
	 *
	 * @param order 排序参数
	 * @return 排序条件集合
	 */
	private List<SortOptions> buildSort(String order) {
		String sortField = FIELD_HOT_SCORE;
		SortOrder sortOrder = SortOrder.Desc;
		if (StringUtils.hasText(order)) {
			String[] orderParts = order.split(":");
			sortField = switch (orderParts[0].trim()) {
				case ORDER_TYPE_PLAY_STAT_NUM -> FIELD_PLAY_STAT_NUM;
				case ORDER_TYPE_CREATE_TIME -> FIELD_CREATE_TIME;
				case ORDER_TYPE_HOT_SCORE -> FIELD_HOT_SCORE;
				default -> {
					log.warn("不支持的排序方式：{}，已按综合排序处理", order);
					yield FIELD_HOT_SCORE;
				}
			};
			if (orderParts.length > 1 && "asc".equalsIgnoreCase(orderParts[1].trim())) {
				sortOrder = SortOrder.Asc;
			}
		}
		String finalSortField = sortField;
		SortOrder finalSortOrder = sortOrder;
		return List.of(
				SortOptions.of(sort -> sort.field(field -> field.field(finalSortField).order(finalSortOrder))),
				SortOptions.of(sort -> sort.field(field -> field.field(FIELD_ID).order(SortOrder.Desc))));
	}


	/**
	 * 解析标签筛选条件
	 *
	 * @param attribute 形如「15:32」的标签条件（标签id:标签值id）
	 * @return [标签id, 标签值id]，格式不对时返回 null
	 */
	private long[] parseAttribute(String attribute) {
		if (!StringUtils.hasText(attribute) || !attribute.contains(":")) {
			log.warn("专辑标签筛选条件格式不正确，已忽略：{}", attribute);
			return null;
		}
		String[] attributeParts = attribute.split(":");
		try {
			return new long[]{Long.parseLong(attributeParts[0].trim()), Long.parseLong(attributeParts[1].trim())};
		} catch (Exception e) {
			log.warn("专辑标签筛选条件格式不正确，已忽略：{}", attribute);
			return null;
		}
	}


	/**
	 * 计算专辑热度
	 * <p>
	 * 购买、评论的行为成本比播放高，所以权重更大；结果保留一位小数，避免浮点误差影响排序结果
	 *
	 * @param albumInfoIndex 索引数据
	 * @return 热度分值
	 */
	private double calculateHotScore(AlbumInfoIndex albumInfoIndex) {
		double hotScore = this.toInt(albumInfoIndex.getPlayStatNum()) * WEIGHT_PLAY
				+ this.toInt(albumInfoIndex.getSubscribeStatNum()) * WEIGHT_SUBSCRIBE
				+ this.toInt(albumInfoIndex.getBuyStatNum()) * WEIGHT_BUY
				+ this.toInt(albumInfoIndex.getCommentStatNum()) * WEIGHT_COMMENT;
		return Math.round(hotScore * 10) / 10.0;
	}


	/**
	 * 组装索引里的专辑标签集合
	 *
	 * @param attributeValueList 专辑标签集合
	 * @return 索引标签集合（只保留标签id与标签值id）
	 */
	private List<AttributeValueIndex> buildAttributeValueIndexList(List<AlbumAttributeValue> attributeValueList) {
		if (CollectionUtils.isEmpty(attributeValueList)) {
			return new ArrayList<>();
		}
		return attributeValueList.stream()
				.filter(attributeValue -> attributeValue.getAttributeId() != null && attributeValue.getValueId() != null)
				.map(attributeValue -> {
					AttributeValueIndex attributeValueIndex = new AttributeValueIndex();
					attributeValueIndex.setAttributeId(attributeValue.getAttributeId());
					attributeValueIndex.setValueId(attributeValue.getValueId());
					return attributeValueIndex;
				}).collect(Collectors.toList());
	}


	/**
	 * 写入搜索建议：同一个专辑三种输入方式各存一份，completion 类型由索引映射决定
	 *
	 * @param albumInfo 专辑信息
	 */
	private void saveSuggestIndex(AlbumInfo albumInfo) {
		String albumTitle = albumInfo.getAlbumTitle();
		if (!StringUtils.hasText(albumTitle)) {
			return;
		}
		SuggestIndex suggestIndex = new SuggestIndex();
		suggestIndex.setId(String.valueOf(albumInfo.getId()));
		suggestIndex.setTitle(albumTitle);
		//中文标题
		suggestIndex.setKeyword(new Completion(new String[]{albumTitle}));
		//全拼：例如「三国演义」-> sanguoyanyi
		suggestIndex.setKeywordPinyin(new Completion(new String[]{PinYinUtils.toHanyuPinyin(albumTitle)}));
		//拼音首字母：例如「三国演义」-> sgyy
		suggestIndex.setKeywordSequence(new Completion(new String[]{PinYinUtils.getFirstLetter(albumTitle)}));
		elasticsearchOperations.save(suggestIndex);
	}


	/**
	 * 删除索引文档：文档可能本来就不存在（重复下架消息），失败只记日志
	 *
	 * @param id    文档ID（专辑ID）
	 * @param clazz 索引实体类型
	 */
	private void deleteIndexDocument(String id, Class<?> clazz) {
		try {
			elasticsearchOperations.delete(id, clazz);
		} catch (Exception e) {
			log.warn("删除索引文档失败（文档可能不存在），id：{}，索引实体：{}", id, clazz.getSimpleName(), e);
		}
	}


	/**
	 * 排行榜排序维度白名单
	 *
	 * @param dimension 前端传入的维度
	 * @return 索引库中真实的字段名
	 */
	private String toRankingSortField(String dimension) {
		if (!StringUtils.hasText(dimension)) {
			return FIELD_HOT_SCORE;
		}
		return switch (dimension) {
			case FIELD_HOT_SCORE, FIELD_PLAY_STAT_NUM, FIELD_SUBSCRIBE_STAT_NUM,
					FIELD_BUY_STAT_NUM, FIELD_COMMENT_STAT_NUM -> dimension;
			default -> {
				log.warn("不支持的排行榜维度：{}，已按热度排序", dimension);
				yield FIELD_HOT_SCORE;
			}
		};
	}


	/**
	 * 把聚合结果里的专辑JSON转成索引实体
	 *
	 * @param source 聚合中 top_hits 的文档
	 * @return 索引实体，解析失败返回 null
	 */
	private AlbumInfoIndex toAlbumInfoIndexFromJson(JsonData source) {
		try {
			return source.to(AlbumInfoIndex.class);
		} catch (Exception e) {
			log.error("解析聚合结果中的专辑数据失败", e);
			return null;
		}
	}


	/**
	 * 索引数据转页面展示对象
	 *
	 * @param albumInfoIndex 索引数据
	 * @return 页面展示对象
	 */
	private AlbumInfoIndexVo toAlbumInfoIndexVo(AlbumInfoIndex albumInfoIndex) {
		AlbumInfoIndexVo albumInfoIndexVo = new AlbumInfoIndexVo();
		BeanUtils.copyProperties(albumInfoIndex, albumInfoIndexVo);
		return albumInfoIndexVo;
	}


	/**
	 * 远程调用结果统一取值：服务降级、业务异常时 data 都是 null，调用方按「没查到」处理
	 *
	 * @param result 远程调用结果
	 * @return 业务数据
	 */
	private <T> T getData(Result<T> result) {
		return result == null ? null : result.getData();
	}


	/**
	 * 聚合桶的 key（三级分类ID）转 Long
	 *
	 * @param fieldValue 聚合桶的key
	 * @return 三级分类ID
	 */
	private Long toLong(FieldValue fieldValue) {
		if (fieldValue == null) {
			return null;
		}
		if (fieldValue.isLong()) {
			return fieldValue.longValue();
		}
		if (fieldValue.isDouble()) {
			return (long) fieldValue.doubleValue();
		}
		if (fieldValue.isString()) {
			try {
				return Long.parseLong(fieldValue.stringValue());
			} catch (NumberFormatException e) {
				log.warn("聚合结果的分类ID不是数字：{}", fieldValue.stringValue());
			}
		}
		return null;
	}


	/**
	 * @param value 可能为 null 的计数
	 * @return 计数，null 按 0 处理
	 */
	private int toInt(Integer value) {
		return value == null ? 0 : value;
	}


	/**
	 * 获取专辑布隆过滤器：已经存在时 tryInit 会直接返回 false，可以放心重复调用
	 *
	 * @return 布隆过滤器
	 */
	private RBloomFilter<Long> getAlbumBloomFilter() {
		RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
		bloomFilter.tryInit(BLOOM_FILTER_EXPECTED_INSERTIONS, BLOOM_FILTER_FALSE_PROBABILITY);
		return bloomFilter;
	}

}
