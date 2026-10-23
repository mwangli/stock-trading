// AI_GENERATE_START---
package com.stock.agent.decision.vo;

/**
 * Java 决策融合结果。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum TradeDecisionFusionOutcome {

    /** 确定性基线允许候选继续进入后续 Java 复核。 */
    BASELINE_APPROVED,

    /** 确定性基线已拒绝候选。 */
    BASELINE_REJECTED,

    /** GATED 模式根据有效 HOLD 或 SKIP 判断跳过自动买入。 */
    AGENT_GATED_SKIP,

    /** Agent 判断不可用且配置要求转人工复核。 */
    MANUAL_REVIEW,

    /** Agent 判断不可用，按显式策略继续采用 Java 基线。 */
    BASELINE_FALLBACK
}
// AI_GENERATE_END---
