// AI_GENERATE_START- -
package com.stock.data.dto;

import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.enums.NewsDocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 跨业务消费的规范新闻事件事实。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketNewsEventDto {
    /** 事件事实标识。 */
    private String id;
    /** 规范事件标识。 */
    private String canonicalEventId;
    /** 股票代码。 */
    private String stockCode;
    /** 股票名称。 */
    private String stockName;
    /** 规范化标题。 */
    private String normalizedTitle;
    /** 规范化正文。 */
    private String normalizedContent;
    /** 文档类型。 */
    private NewsDocumentType documentType;
    /** 文档业务角色。 */
    private NewsDocumentRole documentRole;
    /** 事件类型。 */
    private String eventType;
    /** 内容哈希。 */
    private String contentHash;
    /** 信息优先级。 */
    private Integer informationPriority;
    /** 新颖度分数，范围 0 至 1。 */
    private Double noveltyScore;
    /** 重要性分数，范围 0 至 1。 */
    private Double materialityScore;
    /** 个股相关度，范围 0 至 1。 */
    private Double stockSpecificity;
    /** 发布时间。 */
    private LocalDateTime publishTime;
    /** 来源外部标识集合。 */
    private Set<String> sourceExternalIds;
    /** 来源名称集合。 */
    private Set<String> sources;
    /** 采集处理链版本。 */
    private String pipelineVersion;
}
// AI_GENERATE_END- -
