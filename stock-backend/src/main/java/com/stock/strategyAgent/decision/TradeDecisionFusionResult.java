// AI_GENERATE_START -
package com.stock.strategyAgent.decision;

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

    /** Agent 是否改变最终结果，OFF/SHADOW 固定为 false。 */
    boolean impacted;

    /** 融合规则说明。 */
    String reason;
}
// AI_GENERATE_END -
