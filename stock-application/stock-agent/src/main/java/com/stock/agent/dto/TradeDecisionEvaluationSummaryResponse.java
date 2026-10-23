// AI_GENERATE_START---
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 候选批次的 Agent 有无参与反事实摘要。
 * 动作数量用于表达候选级换手代理，不代表真实成交金额或真实换手率。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionEvaluationSummaryResponse {

    /** 已完成目标日行情评估的候选数量。 */
    int evaluatedCount;

    /** 确定性基线假设执行的候选动作数量。 */
    int baselineActionCount;

    /** Agent 假设参与后保留的候选动作数量。 */
    int agentCounterfactualActionCount;

    /** Agent 假设规避的基线买入数量。 */
    int avoidedActionCount;

    /** 候选动作减少率，范围 0 至 1。 */
    BigDecimal actionReductionRate;

    /** 基线在全部合格候选上的平均收盘收益贡献。 */
    BigDecimal baselineAverageReturnRate;

    /** Agent 假设参与后在同一候选集合上的平均收盘收益贡献。 */
    BigDecimal agentCounterfactualAverageReturnRate;

    /** Agent 假设参与相对基线的平均收益贡献差。 */
    BigDecimal averageReturnDifferenceRate;

    /** 基线候选的最差收盘收益。 */
    BigDecimal baselineWorstReturnRate;

    /** Agent 假设参与后的最差收盘收益贡献。 */
    BigDecimal agentCounterfactualWorstReturnRate;
}
// AI_GENERATE_END---
