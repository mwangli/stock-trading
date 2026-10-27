// AI_GENERATE_START----
package com.stock.agent.enums;

/**
 * Agent 对单个候选给出的结构化建议。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum TradeDecisionRecommendation {

    /** 建议保留买入候选。 */
    BUY,

    /** 建议继续观察，不改变当前基线。 */
    HOLD,

    /** 建议跳过该候选。 */
    SKIP,

    /** 证据不足或运行失败，明确弃权。 */
    ABSTAIN
}
// AI_GENERATE_END----