// AI_GENERATE_START -----
package com.stock.agent.service;

import com.stock.agent.model.BackendTradeDecisionContextFact;
import com.stock.agent.model.BackendTradeDecisionNewsFact;
import com.stock.agent.model.TradeDecisionEvaluationFact;
import com.stock.agent.model.TradeDecisionEvaluationFactRequest;
import com.stock.agent.model.TradeDecisionEvaluationFacts;
import com.stock.platform.dto.InternalTradeDecisionContextResponse;
import com.stock.platform.dto.InternalTradeDecisionEvaluationFactRequest;
import com.stock.platform.service.InternalTradeDecisionContextService;
import com.stock.platform.service.InternalTradeDecisionEvaluationFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询 Agent 交易建议所需的同 JVM 权威业务事实。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class TradeDecisionFactService {

    private final InternalTradeDecisionContextService contextService;
    private final InternalTradeDecisionEvaluationFactsService evaluationFactsService;

    /** 查询并转换 Agent 交易建议所需的权威事实。 */
    public List<BackendTradeDecisionContextFact> tradeDecisionContexts(
            String candidateBatchId, int maxCandidates) {
        return contextService.buildBatch(candidateBatchId, maxCandidates).stream()
                .map(this::toContextFact)
                .toList();
    }

    /** 查询并转换 Agent 交易建议所需的权威事实。 */
    public TradeDecisionEvaluationFacts tradeDecisionEvaluationFacts(
            List<TradeDecisionEvaluationFactRequest> items) {
        var requests = items.stream()
                .map(item -> new InternalTradeDecisionEvaluationFactRequest(
                        item.candidateBatchId(), item.stockCode(), item.targetTradeDate()))
                .toList();
        var wire = evaluationFactsService.query(requests);
        var facts = wire.items().stream()
                .map(item -> new TradeDecisionEvaluationFact(
                        item.candidateBatchId(), item.stockCode(), item.targetTradeDate(),
                        item.targetOpenPrice(), item.targetHighPrice(), item.targetLowPrice(),
                        item.targetClosePrice(), item.executionBatchId(), item.executionBatchStatus(),
                        item.executionProcessedCount(), item.executionSuccessCount()))
                .toList();
        return new TradeDecisionEvaluationFacts(wire.latestMarketDate(), facts);
    }

    private BackendTradeDecisionContextFact toContextFact(InternalTradeDecisionContextResponse wire) {
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
// AI_GENERATE_END ----