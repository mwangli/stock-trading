// AI_GENERATE_START-----
package com.stock.agent.decision.service;

import com.stock.base.trading.TradeDecisionGatePort;
import com.stock.base.trading.TradeDecisionGateResult;

import com.stock.agent.decision.entity.AgentTradeDecisionEntity;
import com.stock.agent.decision.repository.AgentTradeDecisionRepository;
import com.stock.agent.decision.vo.TradeDecisionFusionResult;
import com.stock.agent.decision.vo.TradeDecisionMode;
import com.stock.agent.decision.vo.TradeDecisionModeResolution;
import com.stock.agent.decision.vo.TradeDecisionRecommendation;
import com.stock.agent.decision.vo.TradeDecisionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 自动买入前的 Agent 单向门禁读取服务。
 * 只读取已持久化判断，不在下单线程调用模型，也不改变候选金额和排序。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class TradeDecisionGateService implements TradeDecisionGatePort {

    private final TradeDecisionModeService modeService;
    private final AgentTradeDecisionRepository decisionRepository;
    private final TradeDecisionFusionService fusionService;

    /**
     * 评估一个 Java 基线候选是否允许继续进入自动买入复核链。
     *
     * @param candidateBatchId 候选批次标识
     * @param stockCode 股票代码
     * @param strategyVersion 策略配置版本
     * @param baselineEligible Java 基线是否允许该候选
     * @param evaluatedAt 当前复核时间
     * @return 门禁结果
     */
    @Override
    public TradeDecisionGateResult evaluate(String candidateBatchId,
                                            String stockCode,
                                            String strategyVersion,
                                            boolean baselineEligible,
                                            LocalDateTime evaluatedAt) {
        TradeDecisionFusionResult fusion = evaluateAutomaticBuy(
                candidateBatchId, stockCode, strategyVersion, baselineEligible, evaluatedAt);
        return new TradeDecisionGateResult(
                fusion.isAutomaticBuyAllowed(), fusion.getOutcome().name(), fusion.getReason());
    }

    /**
     * 计算 Agent 与 Java 基线的融合结果。
     *
     * @param candidateBatchId 候选批次标识
     * @param stockCode 股票代码
     * @param strategyVersion 策略配置版本
     * @param baselineEligible Java 基线是否允许该候选
     * @param now 当前复核时间
     * @return 确定性融合结果
     */
    public TradeDecisionFusionResult evaluateAutomaticBuy(String candidateBatchId,
                                                           String stockCode,
                                                           String strategyVersion,
                                                           boolean baselineEligible,
                                                           LocalDateTime now) {
        TradeDecisionModeResolution resolution = modeService.resolve(candidateBatchId, strategyVersion);
        if (resolution.getEffectiveMode() != TradeDecisionMode.GATED
                || !resolution.isProductionImpactAllowed()) {
            return fusionService.fuse(baselineEligible, null, null, null,
                    resolution.getEffectiveMode(), resolution.getFallbackPolicy(), now);
        }
        AgentTradeDecisionEntity decision = decisionRepository
                .findFirstByCandidateBatchIdAndStockCodeAndDecisionModeAndStrategyVersionOrderByCreatedAtDesc(
                        candidateBatchId, stockCode, TradeDecisionMode.GATED.name(), strategyVersion)
                .orElse(null);
        if (decision == null) {
            return fusionService.fuse(baselineEligible, TradeDecisionRecommendation.ABSTAIN,
                    TradeDecisionStatus.ABSTAINED, null, TradeDecisionMode.GATED,
                    resolution.getFallbackPolicy(), now);
        }
        return fusionService.fuse(baselineEligible,
                TradeDecisionRecommendation.valueOf(decision.getRecommendation()),
                TradeDecisionStatus.valueOf(decision.getStatus()), decision.getValidUntil(),
                TradeDecisionMode.GATED, resolution.getFallbackPolicy(), now);
    }
}
// AI_GENERATE_END-----
