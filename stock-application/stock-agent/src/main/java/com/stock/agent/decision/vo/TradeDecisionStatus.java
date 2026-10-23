// AI_GENERATE_START---
package com.stock.agent.decision.vo;

/**
 * Agent 交易决策记录状态。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum TradeDecisionStatus {

    /** 决策结构和证据校验通过。 */
    VALID,

    /** Agent 主动或因 Provider 降级而弃权。 */
    ABSTAINED,

    /** 决策结构或上下文校验失败。 */
    INVALID,

    /** 决策已超过有效期。 */
    EXPIRED,

    /** 决策已被同一上下文的新版本替代。 */
    SUPERSEDED
}
// AI_GENERATE_END---
