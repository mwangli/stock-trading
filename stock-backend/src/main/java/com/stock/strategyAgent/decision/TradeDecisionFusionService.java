// AI_GENERATE_START -
package com.stock.strategyAgent.decision;

import org.springframework.stereotype.Service;

/**
 * Agent 交易判断的确定性融合服务。
 * 当前只支持 OFF 和 SHADOW，任何 Agent 建议都不能改变 Java 基线结果。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
public class TradeDecisionFusionService {

    /**
     * 将基线资格与 Agent 建议进行受限融合。
     *
     * @param baselineEligible 候选是否通过确定性基线
     * @param recommendation Agent 结构化建议
     * @param mode 决策参与模式
     * @return 不影响真实交易的融合结果
     * @throws IllegalArgumentException 当模式不是 OFF 或 SHADOW 时抛出
     */
    public TradeDecisionFusionResult fuse(boolean baselineEligible,
                                           TradeDecisionRecommendation recommendation,
                                           TradeDecisionMode mode) {
        if (mode != TradeDecisionMode.OFF && mode != TradeDecisionMode.SHADOW) {
            throw new IllegalArgumentException("当前决策融合仅允许 OFF 或 SHADOW 模式");
        }
        TradeDecisionFusionOutcome outcome = baselineEligible
                ? TradeDecisionFusionOutcome.BASELINE_APPROVED
                : TradeDecisionFusionOutcome.BASELINE_REJECTED;
        String reason = mode == TradeDecisionMode.OFF
                ? "OFF 模式只保留确定性基线，不采用 Agent 判断"
                : "SHADOW 模式仅记录 Agent 建议 " + recommendation + "，最终结果保持确定性基线";
        return TradeDecisionFusionResult.builder()
                .outcome(outcome)
                .impacted(false)
                .reason(reason)
                .build();
    }
}
// AI_GENERATE_END -
