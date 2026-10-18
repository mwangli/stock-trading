// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感模型人工标注评估样本。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentimentEvaluationCase {

    /** 样本唯一标识。 */
    private String id;

    /** 待评估新闻或公告文本。 */
    private String text;

    /** 人工标注标签：positive、neutral 或 negative。 */
    private String expectedLabel;

    /** 人工标注事件类型，与 StockNews.eventType 使用同一枚举值。 */
    private String expectedEventType;

    /** 人工标注影响方向：BULLISH、BEARISH 或 NEUTRAL。 */
    private String expectedDirection;

    /** 人工标注影响强度，范围为 0 到 1。 */
    private double expectedImpactMagnitude;

    /** 人工标注有效窗口，单位为交易日。 */
    private int effectiveHorizonDays;

    /** 人工标注策略动作：ENHANCE、REDUCE、NO_ACTION 或 RISK_BLOCK。 */
    private String expectedAction;

    /** 重复报道归属的事件组标识，相同事件应使用同一值。 */
    private String duplicateGroupId;

    /** 样本来源分类：新闻或公告。 */
    private String sourceCategory;
}
// AI_GENERATE_END -
