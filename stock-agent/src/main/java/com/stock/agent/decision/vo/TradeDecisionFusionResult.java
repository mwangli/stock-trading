// AI_GENERATE_START---
package com.stock.agent.decision.vo;

import lombok.Builder;
import lombok.Value;

/**
 * Java 确定性融合结果。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionFusionResult {

    /** 最终基线结果。 */
    TradeDecisionFusionOutcome outcome;

    /** Agent 是否改变最终结果。 */
    boolean impacted;

    /** 当前候选是否允许继续进入自动买入 Java 复核链。 */
    boolean automaticBuyAllowed;

    /** 融合规则说明。 */
    String reason;
}
// AI_GENERATE_END---
