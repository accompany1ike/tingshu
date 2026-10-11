package com.atguigu.tingshu.model.search;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 专辑索引库文档：albuminfo
 * <p>
 * 字段类型取舍（索引库一旦建好再改类型就得重建索引，所以这里定死）：
 * 1.需要全文检索的字段用 text + ik 分词：albumTitle、albumIntro（能分词，但不能排序、聚合）；
 * 2.需要精确等值、聚合、排序的字段用 keyword：announcerName、payType、coverUrl（不分词）；
 * 3.分类ID、统计数、热度这些参与过滤与排序的用数值类型：long / integer / double；
 * 4.专辑标签是一对多结构（attributeId + valueId），必须用 Nested，
 * 否则 ES 会把同一个专辑的多个标签拍平成一个数组，出现「标签A的值 + 标签B的值」也能命中的错配。
 */
@Data
@Document(indexName = "albuminfo")
@JsonIgnoreProperties(ignoreUnknown = true)//目的：防止json字符串转成实体对象时因未识别字段报错
public class AlbumInfoIndex implements Serializable {

    private static final long serialVersionUID = 1L;

    // 专辑Id
    @Id
    private Long id;

    //  es 中能分词的字段，这个字段数据类型必须是 text！keyword 不分词！
    //  建索引时用 ik_max_word（拆得细，召回全），检索时用 ik_smart（拆得粗，结果准）
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String albumTitle;

    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String albumIntro;

    @Field(type = FieldType.Keyword)
    private String announcerName;

    //专辑封面
    @Field(type = FieldType.Keyword, index = false)
    private String coverUrl;

    //专辑包含声音总数
    @Field(type = FieldType.Long, index = false)
    private Integer includeTrackCount;

    //专辑是否完结：0-否；1-完结
    @Field(type = FieldType.Long, index = false)
    private String isFinished;

    //付费类型：免费、vip免费、付费
    @Field(type = FieldType.Keyword, index = false)
    private String payType;

    @Field(type = FieldType.Date,format = DateFormat.date_time, pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime; //

    @Field(type = FieldType.Long)
    private Long category1Id;

    @Field(type = FieldType.Long)
    private Long category2Id;

    @Field(type = FieldType.Long)
    private Long category3Id;

    //播放量
    @Field(type = FieldType.Integer)
    private Integer playStatNum = 0;

    //订阅量
    @Field(type = FieldType.Integer)
    private Integer subscribeStatNum = 0;

    //购买量
    @Field(type = FieldType.Integer)
    private Integer buyStatNum = 0;

    //评论数
    @Field(type = FieldType.Integer)
    private Integer commentStatNum = 0;

    //  商品的热度！
    @Field(type = FieldType.Double)
    private Double hotScore = 0d;

    //专辑属性值
    // Nested 支持嵌套查询
    @Field(type = FieldType.Nested)
    private List<AttributeValueIndex> attributeValueIndexList;

}
