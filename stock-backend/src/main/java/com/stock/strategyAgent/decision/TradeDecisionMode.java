// AI_GENERATE_START -
package com.stock.strategyAgent.decision;

/**
 * Agent 交易决策参与模式。
 * 当前实现只允许 OFF 和 SHADOW，生产影响模式留待后续准入评审。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum TradeDecisionMode {

    /** 不生成或不采用 Agent 决策。 */
    OFF,

    /** 记录 Agent 影子判断，但最终交易决策保持确定性基线。 */
    SHADOW
}
// AI_GENERATE_END -
