// AI_GENERATE_START---
package com.stock.agent.decision.vo;

/**
 * Agent 交易决策参与模式。
 * GATED 仅允许减少或延迟 Java 基线允许的买入候选，不得提升基线拒绝项。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum TradeDecisionMode {

    /** 不生成或不采用 Agent 决策。 */
    OFF,

    /** 记录 Agent 影子判断，但最终交易决策保持确定性基线。 */
    SHADOW,

    /** 展示 Agent 建议和风险，但自动交易仍保持确定性基线。 */
    ADVISORY,

    /** 允许有效的 HOLD 或 SKIP 单向减少或延迟自动买入。 */
    GATED
}
// AI_GENERATE_END---
