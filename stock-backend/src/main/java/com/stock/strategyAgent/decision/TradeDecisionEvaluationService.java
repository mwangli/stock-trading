// AI_GENERATE_START ----
package com.stock.strategyAgent.decision;

import com.alibaba.fastjson2.JSON;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.strategyAgent.api.TradeDecisionEvaluationBatchResponse;
import com.stock.strategyAgent.api.TradeDecisionEvaluationResponse;
import com.stock.strategyAgent.api.TradeDecisionEvaluationSummaryResponse;
import com.stock.strategyAgent.api.TradeDecisionProviderMetricResponse;
import com.stock.strategyAgent.api.TradeDecisionProviderStatisticsResponse;
import com.stock.tradingExecutor.domain.entity.AutomaticTradeExecutionBatch;
import com.stock.tradingExecutor.persistence.AutomaticTradeExecutionBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 影子交易判断评估与 Provider 稳定性统计服务。
 * 使用固化上下文和目标交易日日线计算反事实指标，不修改真实交易记录。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class TradeDecisionEvaluationService {

    private static final String MARKET_DATA_EVALUATED = "EVALUATED";
    private static final String MARKET_DATA_WAITING = "WAITING_MARKET_DATA";
    private static final String MARKET_DATA_CONTEXT_UNAVAILABLE = "CONTEXT_UNAVAILABLE";
    private static final int MAX_EVALUATION_BATCH_SIZE = 500;

    private final AgentTradeDecisionRepository decisionRepository;
    private final TradeDecisionContextRepository contextRepository;
    private final TradeDecisionEvaluationRepository evaluationRepository;
    private final PriceRepository priceRepository;
    private final AutomaticTradeExecutionBatchRepository executionBatchRepository;

    /**
     * 评估行情已经到达的影子交易判断。
     *
     * 等待行情的历史记录会优先重试，新判断按创建时间顺序补齐。
     *
     * @param maxItems 单次最多处理数量，范围 1 至 500
     * @return 本次完成或更新的评估数量
     * @throws IllegalArgumentException 当处理数量越界时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public int evaluatePending(int maxItems) {
        if (maxItems < 1 || maxItems > MAX_EVALUATION_BATCH_SIZE) {
            throw new IllegalArgumentException("单次评估数量必须在 1 至 500 之间");
        }
        LocalDate latestMarketDate = priceRepository.findTopByOrderByDateDesc()
                .map(StockPrice::getDate)
                .orElse(null);
        if (latestMarketDate == null) {
            return 0;
        }

        Map<String, AgentTradeDecisionEntity> candidates = new LinkedHashMap<>();
        evaluationRepository.findTop100ByMarketDataStatusOrderByEvaluatedAtAsc(MARKET_DATA_WAITING)
                .stream()
                .map(TradeDecisionEvaluationEntity::getDecisionId)
                .map(decisionRepository::findById)
                .flatMap(java.util.Optional::stream)
                .forEach(decision -> candidates.put(decision.getDecisionId(), decision));
        decisionRepository.findPendingEvaluations(PageRequest.of(0, maxItems))
                .forEach(decision -> candidates.putIfAbsent(decision.getDecisionId(), decision));

        int evaluated = 0;
        for (AgentTradeDecisionEntity decision : candidates.values()) {
            if (evaluated >= maxItems) {
                break;
            }
            TradeDecisionContextEntity context = contextRepository.findById(decision.getContextId())
                    .orElse(null);
            if (context != null && context.getTargetTradeDate() != null
                    && context.getTargetTradeDate().isAfter(latestMarketDate)) {
                continue;
            }
            evaluateOne(decision, context);
            evaluated++;
        }
        return evaluated;
    }

    /**
     * 查询候选批次的影子决策评估结果。
     *
     * @param candidateBatchId 候选批次标识
     * @return 候选批次评估明细
     */
    @Transactional(readOnly = true)
    public TradeDecisionEvaluationBatchResponse listBatch(String candidateBatchId) {
        if (!StringUtils.hasText(candidateBatchId)) {
            throw new IllegalArgumentException("候选批次标识不能为空");
        }
        List<TradeDecisionEvaluationEntity> entities = evaluationRepository
                .findByCandidateBatchIdOrderByEvaluatedAtAsc(candidateBatchId.trim());
        List<TradeDecisionEvaluationResponse> items = entities.stream()
                .map(this::toResponse)
                .toList();
        return TradeDecisionEvaluationBatchResponse.builder()
                .candidateBatchId(candidateBatchId.trim())
                .total(items.size())
                .summary(summarize(entities))
                .items(items)
                .build();
    }

    /**
     * 统计指定时间窗口内的 Provider 稳定性。
     *
     * @param from 起始时间，包含
     * @param to 结束时间，包含
     * @return 按 Provider 和模型分组的稳定性指标
     */
    @Transactional(readOnly = true)
    public TradeDecisionProviderStatisticsResponse providerStatistics(
            LocalDateTime from, LocalDateTime to) {
        LocalDateTime safeTo = to == null ? LocalDateTime.now() : to;
        LocalDateTime safeFrom = from == null ? safeTo.minusDays(30) : from;
        if (safeFrom.isAfter(safeTo)) {
            throw new IllegalArgumentException("统计开始时间不能晚于结束时间");
        }
        List<AgentTradeDecisionEntity> decisions = decisionRepository
                .findByCreatedAtBetweenOrderByCreatedAtAsc(safeFrom, safeTo);
        Map<String, ProviderAccumulator> grouped = new LinkedHashMap<>();
        for (AgentTradeDecisionEntity decision : decisions) {
            String provider = normalizedName(decision.getProvider());
            String model = normalizedName(decision.getModel());
            ProviderAccumulator accumulator = grouped.computeIfAbsent(
                    provider + "\u0000" + model, key -> new ProviderAccumulator(provider, model));
            accumulator.add(decision, parseReasonCodes(decision.getReasonCodes()),
                    parseStringList(decision.getSupportingEvidenceRefs()));
        }
        List<TradeDecisionProviderMetricResponse> providers = grouped.values().stream()
                .map(ProviderAccumulator::toResponse)
                .sorted(Comparator.comparing(TradeDecisionProviderMetricResponse::getProvider)
                        .thenComparing(TradeDecisionProviderMetricResponse::getModel))
                .toList();
        return TradeDecisionProviderStatisticsResponse.builder()
                .from(safeFrom)
                .to(safeTo)
                .totalCount(decisions.size())
                .providers(providers)
                .build();
    }

    private void evaluateOne(AgentTradeDecisionEntity decision, TradeDecisionContextEntity context) {
        TradeDecisionEvaluationEntity evaluation = evaluationRepository.findByDecisionId(decision.getDecisionId())
                .orElseGet(() -> new TradeDecisionEvaluationEntity()
                        .setEvaluationId(UUID.randomUUID().toString())
                        .setDecisionId(decision.getDecisionId())
                        .setContextId(decision.getContextId())
                        .setCandidateBatchId(decision.getCandidateBatchId())
                        .setStockCode(decision.getStockCode()));
        evaluation.setAgentRecommendation(decision.getRecommendation())
                .setStockExecutionKnown(false)
                .setEvaluatedAt(LocalDateTime.now());
        attachExecutionBatch(evaluation);

        if (!hasEvaluationContext(context)) {
            evaluation.setMarketDataStatus(MARKET_DATA_CONTEXT_UNAVAILABLE)
                    .setCounterfactualOutcome("CONTEXT_UNAVAILABLE");
            evaluationRepository.save(evaluation);
            return;
        }

        evaluation.setBaseTradeDate(context.getBaseTradeDate())
                .setTargetTradeDate(context.getTargetTradeDate())
                .setBaselineEligible(context.getBaselineEligible())
                .setBaselineClosePrice(context.getBaselineClosePrice());
        StockPrice targetPrice = priceRepository.findByCodeAndDate(
                decision.getStockCode(), context.getTargetTradeDate()).orElse(null);
        if (targetPrice == null) {
            evaluation.setMarketDataStatus(MARKET_DATA_WAITING)
                    .setCounterfactualOutcome(resolveCounterfactual(
                            context.getBaselineEligible(), decision.getRecommendation()));
            evaluationRepository.save(evaluation);
            return;
        }

        BigDecimal closeReturn = rate(targetPrice.getClosePrice(), context.getBaselineClosePrice());
        BigDecimal favorable = rate(targetPrice.getHighPrice(), context.getBaselineClosePrice());
        BigDecimal adverse = rate(targetPrice.getLowPrice(), context.getBaselineClosePrice());
        boolean agentWouldAvoid = Boolean.TRUE.equals(context.getBaselineEligible())
                && (TradeDecisionRecommendation.SKIP.name().equals(decision.getRecommendation())
                || TradeDecisionRecommendation.HOLD.name().equals(decision.getRecommendation()));
        evaluation.setTargetOpenPrice(targetPrice.getOpenPrice())
                .setTargetHighPrice(targetPrice.getHighPrice())
                .setTargetLowPrice(targetPrice.getLowPrice())
                .setTargetClosePrice(targetPrice.getClosePrice())
                .setCloseReturnRate(closeReturn)
                .setMaxFavorableRate(favorable)
                .setMaxAdverseRate(adverse)
                .setSkipOpportunityCostRate(agentWouldAvoid ? positivePart(closeReturn) : BigDecimal.ZERO)
                .setRiskAvoidanceBenefitRate(agentWouldAvoid ? negativePart(closeReturn) : BigDecimal.ZERO)
                .setCounterfactualOutcome(resolveCounterfactual(
                        context.getBaselineEligible(), decision.getRecommendation()))
                .setMarketDataStatus(MARKET_DATA_EVALUATED);
        evaluationRepository.save(evaluation);
    }

    private void attachExecutionBatch(TradeDecisionEvaluationEntity evaluation) {
        AutomaticTradeExecutionBatch batch = executionBatchRepository
                .findFirstByCandidateBatchIdOrderByStartedAtDesc(evaluation.getCandidateBatchId())
                .orElse(null);
        evaluation.setExecutionBatchId(batch == null ? null : batch.getBatchId())
                .setExecutionBatchStatus(batch == null ? null : batch.getStatus())
                .setExecutionProcessedCount(batch == null ? null : batch.getProcessedCount())
                .setExecutionSuccessCount(batch == null ? null : batch.getSuccessCount());
    }

    private boolean hasEvaluationContext(TradeDecisionContextEntity context) {
        return context != null
                && context.getBaseTradeDate() != null
                && context.getTargetTradeDate() != null
                && context.getBaselineEligible() != null
                && context.getBaselineClosePrice() != null
                && context.getBaselineClosePrice().compareTo(BigDecimal.ZERO) > 0;
    }

    private String resolveCounterfactual(Boolean baselineEligible, String recommendation) {
        if (!Boolean.TRUE.equals(baselineEligible)) {
            return "BASELINE_REJECTED";
        }
        if (TradeDecisionRecommendation.SKIP.name().equals(recommendation)
                || TradeDecisionRecommendation.HOLD.name().equals(recommendation)) {
            return "AGENT_WOULD_AVOID_BUY";
        }
        if (TradeDecisionRecommendation.BUY.name().equals(recommendation)) {
            return "AGENT_ALIGNS_WITH_BUY";
        }
        return "AGENT_ABSTAINED";
    }

    private BigDecimal rate(BigDecimal target, BigDecimal baseline) {
        if (target == null || baseline == null || baseline.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return target.subtract(baseline)
                .divide(baseline, 8, RoundingMode.HALF_UP);
    }

    private BigDecimal positivePart(BigDecimal value) {
        return value != null && value.signum() > 0 ? value : BigDecimal.ZERO;
    }

    private BigDecimal negativePart(BigDecimal value) {
        return value != null && value.signum() < 0 ? value.negate() : BigDecimal.ZERO;
    }

    private TradeDecisionEvaluationResponse toResponse(TradeDecisionEvaluationEntity entity) {
        return TradeDecisionEvaluationResponse.builder()
                .evaluationId(entity.getEvaluationId())
                .decisionId(entity.getDecisionId())
                .candidateBatchId(entity.getCandidateBatchId())
                .stockCode(entity.getStockCode())
                .baseTradeDate(entity.getBaseTradeDate())
                .targetTradeDate(entity.getTargetTradeDate())
                .baselineEligible(entity.getBaselineEligible())
                .agentRecommendation(entity.getAgentRecommendation())
                .baselineClosePrice(entity.getBaselineClosePrice())
                .targetOpenPrice(entity.getTargetOpenPrice())
                .targetHighPrice(entity.getTargetHighPrice())
                .targetLowPrice(entity.getTargetLowPrice())
                .targetClosePrice(entity.getTargetClosePrice())
                .closeReturnRate(entity.getCloseReturnRate())
                .maxFavorableRate(entity.getMaxFavorableRate())
                .maxAdverseRate(entity.getMaxAdverseRate())
                .skipOpportunityCostRate(entity.getSkipOpportunityCostRate())
                .riskAvoidanceBenefitRate(entity.getRiskAvoidanceBenefitRate())
                .counterfactualOutcome(entity.getCounterfactualOutcome())
                .marketDataStatus(entity.getMarketDataStatus())
                .executionBatchId(entity.getExecutionBatchId())
                .executionBatchStatus(entity.getExecutionBatchStatus())
                .executionProcessedCount(entity.getExecutionProcessedCount())
                .executionSuccessCount(entity.getExecutionSuccessCount())
                .stockExecutionKnown(entity.isStockExecutionKnown())
                .evaluatedAt(entity.getEvaluatedAt())
                .build();
    }

    private TradeDecisionEvaluationSummaryResponse summarize(
            List<TradeDecisionEvaluationEntity> entities) {
        List<TradeDecisionEvaluationEntity> evaluated = entities.stream()
                .filter(item -> MARKET_DATA_EVALUATED.equals(item.getMarketDataStatus()))
                .filter(item -> Boolean.TRUE.equals(item.getBaselineEligible()))
                .filter(item -> item.getCloseReturnRate() != null)
                .toList();
        int baselineActions = evaluated.size();
        int avoidedActions = (int) evaluated.stream().filter(this::agentWouldAvoid).count();
        int agentActions = baselineActions - avoidedActions;
        BigDecimal baselineAverage = average(evaluated.stream()
                .map(TradeDecisionEvaluationEntity::getCloseReturnRate)
                .toList(), baselineActions);
        List<BigDecimal> agentContributions = evaluated.stream()
                .map(item -> agentWouldAvoid(item) ? BigDecimal.ZERO : item.getCloseReturnRate())
                .toList();
        BigDecimal agentAverage = average(agentContributions, baselineActions);
        return TradeDecisionEvaluationSummaryResponse.builder()
                .evaluatedCount(evaluated.size())
                .baselineActionCount(baselineActions)
                .agentCounterfactualActionCount(agentActions)
                .avoidedActionCount(avoidedActions)
                .actionReductionRate(divide(avoidedActions, baselineActions))
                .baselineAverageReturnRate(baselineAverage)
                .agentCounterfactualAverageReturnRate(agentAverage)
                .averageReturnDifferenceRate(agentAverage.subtract(baselineAverage))
                .baselineWorstReturnRate(minimum(evaluated.stream()
                        .map(TradeDecisionEvaluationEntity::getCloseReturnRate).toList()))
                .agentCounterfactualWorstReturnRate(minimum(agentContributions))
                .build();
    }

    private boolean agentWouldAvoid(TradeDecisionEvaluationEntity entity) {
        return TradeDecisionRecommendation.SKIP.name().equals(entity.getAgentRecommendation())
                || TradeDecisionRecommendation.HOLD.name().equals(entity.getAgentRecommendation());
    }

    private BigDecimal average(List<BigDecimal> values, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal divide(int numerator, int denominator) {
        return denominator == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 8, RoundingMode.HALF_UP);
    }

    private BigDecimal minimum(List<BigDecimal> values) {
        return values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }

    private List<String> parseReasonCodes(String json) {
        return parseStringList(json);
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return JSON.parseArray(json, String.class);
        } catch (RuntimeException exception) {
            return List.of("PERSISTED_JSON_INVALID");
        }
    }

    private String normalizedName(String value) {
        return StringUtils.hasText(value) ? value.trim() : "UNKNOWN";
    }

    private static final class ProviderAccumulator {

        private final String provider;
        private final String model;
        private long totalCount;
        private long validCount;
        private long abstainedCount;
        private long timeoutCount;
        private long invalidOutputCount;
        private long unavailableCount;
        private long evidenceGapCount;
        private long baselineFallbackCount;

        private ProviderAccumulator(String provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        private void add(AgentTradeDecisionEntity decision, List<String> reasonCodes,
                         List<String> supportingEvidenceRefs) {
            totalCount++;
            if (TradeDecisionStatus.VALID.name().equals(decision.getStatus())) {
                validCount++;
            } else {
                abstainedCount++;
                baselineFallbackCount++;
            }
            if (reasonCodes.contains("AGENT_PROVIDER_TIMEOUT")) {
                timeoutCount++;
            }
            if (reasonCodes.contains("AGENT_DECISION_INVALID")
                    || reasonCodes.contains("PERSISTED_JSON_INVALID")) {
                invalidOutputCount++;
            }
            if (reasonCodes.contains("AGENT_DECISION_UNAVAILABLE")) {
                unavailableCount++;
            }
            if (supportingEvidenceRefs.isEmpty()) {
                evidenceGapCount++;
            }
        }

        private TradeDecisionProviderMetricResponse toResponse() {
            return TradeDecisionProviderMetricResponse.builder()
                    .provider(provider)
                    .model(model)
                    .totalCount(totalCount)
                    .validCount(validCount)
                    .abstainedCount(abstainedCount)
                    .timeoutCount(timeoutCount)
                    .invalidOutputCount(invalidOutputCount)
                    .unavailableCount(unavailableCount)
                    .evidenceGapCount(evidenceGapCount)
                    .baselineFallbackCount(baselineFallbackCount)
                    .structuredValidRate(rate(validCount, totalCount))
                    .abstainRate(rate(abstainedCount, totalCount))
                    .baselineFallbackRate(rate(baselineFallbackCount, totalCount))
                    .build();
        }

        private double rate(long numerator, long denominator) {
            return denominator == 0L ? 0D : (double) numerator / denominator;
        }
    }
}
// AI_GENERATE_END ----
