// AI_GENERATE_START----
package com.stock.agent.decision.service;

import com.stock.agent.decision.vo.TradeDecisionFallbackPolicy;
import com.stock.agent.decision.vo.TradeDecisionFusionOutcome;
import com.stock.agent.decision.vo.TradeDecisionFusionResult;
import com.stock.agent.decision.vo.TradeDecisionMode;
import com.stock.agent.decision.vo.TradeDecisionRecommendation;
import com.stock.agent.decision.vo.TradeDecisionStatus;
import org.springframework.stereotype.Service;

/**
 * Agent 交易判断的确定性融合服务。
 * 保证 Agent 只能单向减少或延迟 Java 基线允许的自动买入候选。
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
     * @param status Agent 判断状态
     * @param validUntil Agent 判断有效期
     * @param mode 决策参与模式
     * @param fallbackPolicy Agent 不可用时的显式回退策略
     * @param now 当前 Java 复核时间
     * @return 确定性融合结果
     */
    public TradeDecisionFusionResult fuse(boolean baselineEligible,
                                           TradeDecisionRecommendation recommendation,
                                           TradeDecisionStatus status,
                                           java.time.LocalDateTime validUntil,
                                           TradeDecisionMode mode,
                                           TradeDecisionFallbackPolicy fallbackPolicy,
                                           java.time.LocalDateTime now) {
        if (!baselineEligible) {
            return result(TradeDecisionFusionOutcome.BASELINE_REJECTED, false, false,
                    "Java 确定性基线已拒绝候选，Agent 不能提升为可交易状态");
        }
        if (mode == TradeDecisionMode.OFF || mode == TradeDecisionMode.SHADOW) {
            return result(TradeDecisionFusionOutcome.BASELINE_APPROVED, false, true,
                    mode + " 模式不改变 Java 基线结果");
        }
        if (mode == TradeDecisionMode.ADVISORY) {
            return result(TradeDecisionFusionOutcome.BASELINE_APPROVED, false, true,
                    "ADVISORY 模式仅展示 Agent 建议，不改变自动交易结果");
        }
        boolean valid = status == TradeDecisionStatus.VALID
                && recommendation != null
                && recommendation != TradeDecisionRecommendation.ABSTAIN
                && validUntil != null
                && now != null
                && validUntil.isAfter(now);
        if (!valid) {
            return fallback(fallbackPolicy);
        }
        if (recommendation == TradeDecisionRecommendation.HOLD
                || recommendation == TradeDecisionRecommendation.SKIP) {
            return result(TradeDecisionFusionOutcome.AGENT_GATED_SKIP, true, false,
                    "GATED 模式只允许 HOLD 或 SKIP 单向减少或延迟基线买入");
        }
        return result(TradeDecisionFusionOutcome.BASELINE_APPROVED, false, true,
                "Agent BUY 只能确认既有基线候选，不能新增候选或提高额度");
    }

    private TradeDecisionFusionResult fallback(TradeDecisionFallbackPolicy fallbackPolicy) {
        if (fallbackPolicy == TradeDecisionFallbackPolicy.MANUAL_REVIEW) {
            return result(TradeDecisionFusionOutcome.MANUAL_REVIEW, true, false,
                    "Agent 判断缺失、弃权或过期，按配置停止自动买入并转人工复核");
        }
        return result(TradeDecisionFusionOutcome.BASELINE_FALLBACK, false, true,
                "Agent 判断缺失、弃权或过期，按配置继续采用 Java 基线");
    }

    private TradeDecisionFusionResult result(TradeDecisionFusionOutcome outcome,
                                             boolean impacted,
                                             boolean automaticBuyAllowed,
                                             String reason) {
        return TradeDecisionFusionResult.builder()
                .outcome(outcome)
                .impacted(impacted)
                .automaticBuyAllowed(automaticBuyAllowed)
                .reason(reason)
                .build();
    }
}
// AI_GENERATE_END----
