// AI_GENERATE_START--------------
package com.stock.agent.service;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.model.BackendTradeDecisionContextFact;
import com.stock.agent.entity.TradeDecisionContextEntity;
import com.stock.agent.repository.TradeDecisionContextRepository;
import com.stock.agent.vo.TradeDecisionContext;
import com.stock.agent.vo.TradeDecisionNewsSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 交易决策上下文接收与持久化服务。
 * 权威事实由 Backend 固化，Agent 只保存上下文并调用模型。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class TradeDecisionContextService {

    private final TradeDecisionFactService decisionFactService;
    private final TradeDecisionContextRepository contextRepository;

    /**
     * 请求 Backend 构建并在 Agent 侧持久化不可变决策上下文。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量，范围 1 至 20
     * @return 按候选排名排列的决策上下文
     */
    public List<TradeDecisionContext> buildBatch(String candidateBatchId, int maxCandidates) {
        List<TradeDecisionContext> contexts = decisionFactService
                .tradeDecisionContexts(candidateBatchId, maxCandidates).stream()
                .map(this::toContext)
                .toList();
        contexts.forEach(this::persistContext);
        return contexts;
    }

    private TradeDecisionContext toContext(BackendTradeDecisionContextFact fact) {
        String contextId = contextRepository.findByContextHash(fact.contextHash())
                .map(TradeDecisionContextEntity::getContextId)
                .orElse(fact.contextId());
        List<TradeDecisionNewsSnapshot> news = fact.recentNews().stream()
                .map(item -> TradeDecisionNewsSnapshot.builder()
                        .evidenceRef(item.evidenceRef())
                        .title(item.title())
                        .source(item.source())
                        .category(item.category())
                        .eventType(item.eventType())
                        .publishTime(item.publishTime())
                        .build())
                .toList();
        return TradeDecisionContext.builder()
                .contextId(contextId)
                .contextHash(fact.contextHash())
                .candidateBatchId(fact.candidateBatchId())
                .predictionBatchId(fact.predictionBatchId())
                .stockCode(fact.stockCode())
                .stockName(fact.stockName())
                .modelVersion(fact.modelVersion())
                .sentimentModelVersion(fact.sentimentModelVersion())
                .strategyVersion(fact.strategyVersion())
                .baseTradeDate(fact.baseTradeDate())
                .targetTradeDate(fact.targetTradeDate())
                .informationCutoffAt(fact.informationCutoffAt())
                .baselineRank(fact.baselineRank())
                .baselineScore(fact.baselineScore())
                .lstmScore(fact.lstmScore())
                .predictedReturn(fact.predictedReturn())
                .directionProbability(fact.directionProbability())
                .downsideRisk(fact.downsideRisk())
                .newsImpactScore(fact.newsImpactScore())
                .candidateReason(fact.candidateReason())
                .baselineEligible(fact.baselineEligible())
                .closePrice(fact.closePrice())
                .priceTradeDate(fact.priceTradeDate())
                .minimumScore(fact.minimumScore())
                .accountSnapshotAvailable(fact.accountSnapshotAvailable())
                .totalAssets(fact.totalAssets())
                .availableCash(fact.availableCash())
                .accountCapturedAt(fact.accountCapturedAt())
                .dataQualityFlags(List.copyOf(fact.dataQualityFlags()))
                .recentNews(news)
                .evidenceRefs(List.copyOf(fact.evidenceRefs()))
                .asOf(fact.asOf())
                .expiresAt(fact.expiresAt())
                .build();
    }

    private void persistContext(TradeDecisionContext context) {
        if (contextRepository.existsById(context.getContextId())) {
            return;
        }
        contextRepository.save(new TradeDecisionContextEntity()
                .setContextId(context.getContextId())
                .setContextHash(context.getContextHash())
                .setCandidateBatchId(context.getCandidateBatchId())
                .setStockCode(context.getStockCode())
                .setBaseTradeDate(context.getBaseTradeDate())
                .setTargetTradeDate(context.getTargetTradeDate())
                .setBaselineEligible(context.isBaselineEligible())
                .setBaselineClosePrice(context.getClosePrice())
                .setContextJson(JSON.toJSONString(context))
                .setInformationCutoffAt(context.getInformationCutoffAt())
                .setExpiresAt(context.getExpiresAt())
                .setCreatedAt(context.getAsOf()));
    }
}
// AI_GENERATE_END-------------
