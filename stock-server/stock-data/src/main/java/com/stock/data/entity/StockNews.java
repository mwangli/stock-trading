// AI_GENERATE_START---------
package com.stock.data.entity;

import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.enums.NewsDocumentType;
import com.stock.data.enums.NewsQualityStatus;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * 股票新闻原始文档。
 * 保留证券平台原始内容，并保存采集层生成的规范化、分类和事件追踪字段。
 * 同一条新闻可关联多只股票，以 (stockCode, externalId) 联合唯一。
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Data
@Document(collection = "stock_news")
@CompoundIndex(name = "idx_stock_publish", def = "{'stockCode': 1, 'publishTime': -1}")
@CompoundIndex(name = "idx_stock_external", def = "{'stockCode': 1, 'externalId': 1}", unique = true)
public class StockNews {

    /** MongoDB 文档 ID。 */
    @Id
    private String id;

    /** 证券平台新闻 ID，与 stockCode 联合唯一。 */
    private String externalId;

    /** 兼容展示标题，保持供应商原始语义。 */
    private String title;

    /** 兼容展示正文，保持供应商原始语义。 */
    private String content;

    /** 关联股票代码，如 600519。 */
    private String stockCode;

    /** 股票名称，冗余存储便于展示。 */
    private String stockName;

    /** 来源媒体，如上海证券交易所。 */
    private String source;

    /** 供应商原始栏目名称，仅用于兼容展示。 */
    private String category;

    /** 供应商原始菜单编号，不承担真实文档类型语义。 */
    private String rawMenuId;

    /** 供应商原始标题，用于审计和规则重放。 */
    private String rawTitle;

    /** 供应商原始正文，用于审计和规则重放。 */
    private String rawContent;

    /** 规范化标题，供去重、情感推理和事件识别使用。 */
    private String normalizedTitle;

    /** 规范化正文，供去重、情感推理和事件识别使用。 */
    private String normalizedContent;

    /** 原始采集渠道名称，不承担真实文档类型语义。 */
    private String sourceChannel;

    /** 来源原始页面或附件地址，可为空，用于审计追溯。 */
    private String sourceUrl;

    /** 根据来源和正文推导的真实文档类型。 */
    private NewsDocumentType documentType;

    /** 文档在自动交易链路中的业务角色。 */
    private NewsDocumentRole documentRole;

    /** 结构化事件类型，用于情感分析和风险门禁。 */
    private String eventType;

    /** 规范化标题与正文的 SHA-256 摘要。 */
    private String contentHash;

    /** 跨来源规范事件标识。 */
    private String canonicalEventId;

    /** 事件采集优先级，数值范围 1 到 5。 */
    private Integer informationPriority;

    /** 文档新颖度，范围 0 到 1。 */
    private Double noveltyScore;

    /** 事件重要性，范围 0 到 1。 */
    private Double materialityScore;

    /** 股票相关度，范围 0 到 1。 */
    private Double stockSpecificity;

    /** 采集治理管线版本。 */
    private String pipelineVersion;

    /** 采集治理状态。 */
    private NewsQualityStatus qualityStatus;

    /** 采集治理状态原因。 */
    private String qualityReason;

    /** 新闻发布时间。 */
    @Indexed(name = "idx_publish_time", direction = IndexDirection.DESCENDING)
    private LocalDateTime publishTime;

    /** 本地入库时间。 */
    private LocalDateTime createTime;
}
// AI_GENERATE_END---------
