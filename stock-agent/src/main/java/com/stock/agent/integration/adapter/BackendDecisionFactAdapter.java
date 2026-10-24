// AI_GENERATE_START ---
package com.stock.agent.integration.adapter;

import com.stock.agent.decision.port.TradeDecisionFactPort;
import com.stock.agent.decision.port.model.BackendTradeDecisionContextFact;
import com.stock.agent.decision.port.model.BackendTradeDecisionNewsFact;
import com.stock.agent.decision.port.model.TradeDecisionEvaluationFact;
import com.stock.agent.decision.port.model.TradeDecisionEvaluationFactRequest;
import com.stock.agent.decision.port.model.TradeDecisionEvaluationFacts;
import com.stock.agent.integration.client.BackendInternalApiClient;
import com.stock.agent.integration.dto.BackendTradeDecisionContextResponse;
import com.stock.agent.integration.dto.BackendTradeDecisionEvaluationFactRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Backend 内部 HTTP API 的交易建议事实适配器。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class BackendDecisionFactAdapter implements TradeDecisionFactPort {

    private final BackendInternalApiClient client;

    /** {@inheritDoc} */
    @Override
    public List<BackendTradeDecisionContextFact> tradeDecisionContexts(
            String candidateBatchId, int maxCandidates) {
        return client.tradeDecisionContexts(candidateBatchId, maxCandidates).stream()
                .map(this::toContextFact)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public TradeDecisionEvaluationFacts tradeDecisionEvaluationFacts(
            List<TradeDecisionEvaluationFactRequest> items) {
        var requests = items.stream()
                .map(item -> new BackendTradeDecisionEvaluationFactRequest(
                        item.candidateBatchId(), item.stockCode(), item.targetTradeDate()))
                .toList();
        var wire = client.tradeDecisionEvaluationFacts(requests);
        var facts = wire.items().stream()
                .map(item -> new TradeDecisionEvaluationFact(
                        item.candidateBatchId(), item.stockCode(), item.targetTradeDate(),
                        item.targetOpenPrice(), item.targetHighPrice(), item.targetLowPrice(),
                        item.targetClosePrice(), item.executionBatchId(), item.executionBatchStatus(),
                        item.executionProcessedCount(), item.executionSuccessCount()))
                .toList();
        return new TradeDecisionEvaluationFacts(wire.latestMarketDate(), facts);
    }

    private BackendTradeDecisionContextFact toContextFact(BackendTradeDecisionContextResponse wire) {
        List<BackendTradeDecisionNewsFact> news = wire.recentNews().stream()
                .map(item -> new BackendTradeDecisionNewsFact(
                        item.evidenceRef(), item.title(), item.source(), item.category(),
                        item.eventType(), item.publishTime()))
                .toList();
        return new BackendTradeDecisionContextFact(
                wire.contextId(), wire.contextHash(), wire.candidateBatchId(), wire.predictionBatchId(),
                wire.stockCode(), wire.stockName(), wire.modelVersion(), wire.sentimentModelVersion(),
                wire.strategyVersion(), wire.baseTradeDate(), wire.targetTradeDate(),
                wire.informationCutoffAt(), wire.baselineRank(), wire.baselineScore(), wire.lstmScore(),
                wire.predictedReturn(), wire.directionProbability(), wire.downsideRisk(),
                wire.newsImpactScore(), wire.candidateReason(), wire.baselineEligible(),
                wire.closePrice(), wire.priceTradeDate(), wire.minimumScore(),
                wire.accountSnapshotAvailable(), wire.totalAssets(), wire.availableCash(),
                wire.accountCapturedAt(), wire.dataQualityFlags(), news, wire.evidenceRefs(),
                wire.asOf(), wire.expiresAt());
    }
}
// AI_GENERATE_END --
