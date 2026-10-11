package com.atguigu.tingshu.search.config;

import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

/**
 * 索引库初始化：服务启动时保证 albuminfo、suggestinfo 两个索引存在
 * <p>
 * 索引的字段类型（text/keyword/nested/completion）由实体上的注解决定，
 * 提前按注解建好索引，避免 ES 用默认的 dynamic mapping 把 text 字段建成 keyword、
 * 把 nested 建成 object，那样分词检索和标签筛选都会失效（索引建好后类型不能再改，只能重建）
 */
@Slf4j
@Component
@SuppressWarnings({"all"})
public class EsIndexInitializer implements ApplicationRunner {

	@Autowired
	private ElasticsearchOperations elasticsearchOperations;

	@Override
	public void run(ApplicationArguments args) {
		this.createIndexIfAbsent(AlbumInfoIndex.class);
		this.createIndexIfAbsent(SuggestIndex.class);
	}

	/**
	 * 索引不存在时按实体注解建索引；建索引失败只记日志，不影响服务启动
	 * （ES 没起来、ik 分词插件没装都会失败，这两种情况都该由运维处理，而不是让整个服务起不来）
	 *
	 * @param clazz 索引实体类型
	 */
	private void createIndexIfAbsent(Class<?> clazz) {
		try {
			IndexOperations indexOperations = elasticsearchOperations.indexOps(clazz);
			if (indexOperations.exists()) {
				return;
			}
			indexOperations.createWithMapping();
			log.info("索引库创建成功：{}", indexOperations.getIndexCoordinates().getIndexName());
		} catch (Exception e) {
			log.error("索引库初始化失败，索引实体：{}，请检查 Elasticsearch 是否可用、是否已安装 ik 分词插件", clazz.getSimpleName(), e);
		}
	}

}
