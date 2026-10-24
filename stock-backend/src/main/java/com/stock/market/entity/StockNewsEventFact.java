// AI_GENERATE_START----
package com.stock.market.entity;

import com.stock.market.enums.NewsDocumentRole;
import com.stock.market.enums.NewsDocumentType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 股票新闻规范事件事实。
 * 为情感推理、风险门禁和时序模型提供去重后的点时输入。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Document(collection = "stock_news_event_facts")
@CompoundIndexes({
        @CompoundIndex(name = "uk_news_event_stock", def = "{'stockCode': 1, 'canonicalEventId': 1}", unique = true),
        @CompoundIndex(name = "idx_news_event_stock_time", def = "{'stockCode': 1, 'publishTime': -1}"),
        @CompoundIndex(name = "idx_news_event_role_time", def = "{'documentRole': 1, 'publishTime': -1}")
})
public class StockNewsEventFact {

    /** MongoDB 文档 ID。 */
    @Id
    private String id;

    /** 规范事件标识，同一事件跨来源保持一致。 */
    private String canonicalEventId;

    /** 关联股票代码。 */
    private String stockCode;

    /** 关联股票名称。 */
    private String stockName;

    /** 规范化标题。 */
    private String normalizedTitle;

    /** 规范化正文。 */
    private String normalizedContent;

    /** 真实文档类型。 */
    private NewsDocumentType documentType;

    /** 自动交易业务角色。 */
    private NewsDocumentRole documentRole;

    /** 结构化事件类型。 */
    private String eventType;

    /** 规范化文档内容摘要。 */
    private String contentHash;

    /** 信息优先级，范围 1 到 5。 */
    private Integer informationPriority;

    /** 新颖度，范围 0 到 1。 */
    private Double noveltyScore;

    /** 事件重要性，范围 0 到 1。 */
    private Double materialityScore;

    /** 股票相关度，范围 0 到 1。 */
    private Double stockSpecificity;

    /** 首次可用发布时间。 */
    private LocalDateTime publishTime;

    /** 最近一次观察到该事件的时间。 */
    private LocalDateTime lastSeenAt;

    /** 关联的供应商外部文档 ID 集合。 */
    private Set<String> sourceExternalIds = new LinkedHashSet<>();

    /** 关联来源媒体集合。 */
    private Set<String> sources = new LinkedHashSet<>();

    /** 同一规范事件在不同来源中观测到的业务角色集合。 */
    private Set<NewsDocumentRole> observedDocumentRoles = new LinkedHashSet<>();

    /** 采集治理管线版本。 */
    private String pipelineVersion;

    /** 事件事实首次创建时间。 */
    private LocalDateTime createTime;

    /** 事件事实最近更新时间。 */
    private LocalDateTime updateTime;
}
// AI_GENERATE_END----
