// AI_GENERATE_START-----
package com.stock.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 股票新闻事件情感聚合结果。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Data
@Builder
public class SentimentAggregateResult {

    /** 按事件影响聚合后的新闻影响得分，范围为 -1 到 1。 */
    private double newsImpactScore;

    /** 事件影响强度，范围为 0 到 1。 */
    private double impactMagnitude;

    /** 是否达到进入选股排序的最低门槛。 */
    private boolean actionable;

    /** 聚合窗口内权重最高的事件类型。 */
    private String dominantEventType;

    /** 事件影响预计持续时间，单位为小时。 */
    private int effectiveHorizonHours;

    /** 新闻影响方向：BULLISH、BEARISH 或 NEUTRAL。 */
    private String impactDirection;

    /** 策略动作：ENHANCE、REDUCE、NO_ACTION 或 RISK_BLOCK。 */
    private String strategyAction;

    /** 新闻覆盖和模型置信度合成值，范围为 0 到 1。 */
    private double confidence;

    /** 原始新闻和公告数量。 */
    private int newsCount;

    /** 去重后的独立事件数量。 */
    private int uniqueEventCount;

    /** 是否包含应阻断买入的强负面事件。 */
    private boolean strongNegative;

    /** 触发强负面标志的事件标题。 */
    private String strongNegativeTitle;
}
// AI_GENERATE_END-----
