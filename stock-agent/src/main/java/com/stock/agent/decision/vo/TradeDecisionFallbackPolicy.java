// AI_GENERATE_START---
package com.stock.agent.decision.vo;

/**
 * Agent 判断不可用时的显式回退策略。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum TradeDecisionFallbackPolicy {

    /** Agent 缺失、弃权或过期时继续采用 Java 基线结果。 */
    BASELINE_FALLBACK,

    /** Agent 缺失、弃权或过期时停止自动买入并转人工复核。 */
    MANUAL_REVIEW
}
// AI_GENERATE_END---
