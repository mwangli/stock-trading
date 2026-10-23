// AI_GENERATE_START---
package com.stock.agent.decision.vo;

import lombok.Builder;
import lombok.Value;

/**
 * 单个候选批次解析后的决策参与模式。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionModeResolution {

    /** 配置的目标参与模式。 */
    TradeDecisionMode configuredMode;

    /** 结合灰度范围和生产开关后的实际参与模式。 */
    TradeDecisionMode effectiveMode;

    /** Agent 判断不可用时的回退策略。 */
    TradeDecisionFallbackPolicy fallbackPolicy;

    /** 当前批次是否允许 Agent 影响自动买入结果。 */
    boolean productionImpactAllowed;

    /** 模式解析说明。 */
    String reason;
}
// AI_GENERATE_END---
