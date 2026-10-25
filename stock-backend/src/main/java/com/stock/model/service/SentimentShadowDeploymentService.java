// AI_GENERATE_START --------------
package com.stock.model.service;

import com.stock.market.dto.MarketNewsEventDto;
import com.stock.market.enums.NewsDocumentRole;
import com.stock.market.dto.StockPriceItemDto;
import com.stock.market.service.MarketDataQueryService;
import com.stock.market.service.TradingCalendarService;
import com.stock.model.config.PredictionCostProperties;
import com.stock.model.config.SentimentShadowConfig;
import com.stock.model.entity.SentimentModelRoutingStateDocument;
import com.stock.model.entity.SentimentShadowPredictionDocument;
import com.stock.model.repository.SentimentModelRoutingStateRepository;
import com.stock.model.repository.SentimentShadowPredictionRepository;
import com.stock.model.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mengzi 影子推理、后验评估、人工候选和自动回滚编排服务。
 *
 * @see <a href="http://localhost:5173/docs/backend/model/designs/mengzi-shadow-deployment">
 *     Mengzi 影子部署详细设计</a>
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentShadowDeploymentService {

    private final SentimentShadowConfig config;
    private final MarketDataQueryService marketDataQueryService;
    private final SentimentShadowPredictionRepository predictionRepository;
    private final SentimentModelRoutingStateRepository routingStateRepository;
    private final SentimentInferenceService finbertInferenceService;
    private final MengziModelRuntimeService mengziRuntimeService;
    private final SentimentModelRouterService routerService;
    private final TradingCalendarService tradingCalendarService;
    private final PredictionCostProperties costProperties;
    private final SentimentInputAssembler inputAssembler;

    /**
     * 执行一次 Mengzi 影子推理、历史结果评估和自动路由检查。
     *
     * @throws IllegalStateException 影子功能关闭、模型推理或生产模型恢复失败时抛出
     */
    public synchronized void runShadowCycle() {
        if (!config.isEnabled()) {
            throw new IllegalStateException("Mengzi 影子推理未启用");
        }
        boolean mengziWasActive = routerService.isMengziActive();
        boolean finbertWasLoaded = finbertInferenceService.isModelLoaded();
        List<MarketNewsEventDto> facts = loadPendingFacts();
        if (facts.isEmpty()) {
            evaluatePendingPredictions();
            evaluateRoutingState();
            return;
        }
        mengziRuntimeService.unloadModel();
        Map<String, SentimentAnalysisResult> finbertResults = inferFinbertBaseline(facts);
        String finbertModelVersion = finbertInferenceService.getArtifactInspection().getModelVersion();
        finbertInferenceService.unloadModel();
        try {
            mengziRuntimeService.loadModel();
            inferMengziCandidates(facts, finbertResults, finbertModelVersion);
        } catch (RuntimeException exception) {
            recordCycleFailure(exception);
            throw exception;
        } finally {
            mengziRuntimeService.unloadModel();
            if (!mengziWasActive && finbertWasLoaded && !finbertInferenceService.loadModel()) {
                throw new IllegalStateException("Mengzi 影子窗口结束后 FinBERT 恢复失败");
            }
        }
        evaluatePendingPredictions();
        evaluateRoutingState();
    }

    private List<MarketNewsEventDto> loadPendingFacts() {
        LocalDateTime endTime = LocalDateTime.now();
        LocalDateTime startTime = endTime.minusDays(Math.max(1, config.getLookbackDays()));
        int batchSize = Math.max(1, config.getBatchSize());
        List<MarketNewsEventDto> pending = new ArrayList<>();
        for (int page = 0; pending.size() < batchSize; page++) {
            List<MarketNewsEventDto> facts = marketDataQueryService.findNewsEvents(
                    NewsDocumentRole.TRADE_SIGNAL, startTime, endTime, page, batchSize);
            if (facts.isEmpty()) {
                break;
            }
            facts.stream()
                    .filter(fact -> fact.getId() != null && !predictionRepository
                            .existsByEventFactIdAndCandidateModelVersion(
                                    fact.getId(), config.getModelVersion()))
                    .limit(batchSize - pending.size())
                    .forEach(pending::add);
            if (facts.size() < batchSize) {
                break;
            }
        }
        return pending;
    }

    private Map<String, SentimentAnalysisResult> inferFinbertBaseline(List<MarketNewsEventDto> facts) {
        Map<String, SentimentAnalysisResult> results = new LinkedHashMap<>();
        for (MarketNewsEventDto fact : facts) {
            results.put(fact.getId(), finbertInferenceService.analyzeSentimentRequired(
                    inputAssembler.assemble(fact.getNormalizedTitle(), fact.getNormalizedContent())));
        }
        return results;
    }

    private void inferMengziCandidates(
            List<MarketNewsEventDto> facts,
            Map<String, SentimentAnalysisResult> finbertResults,
            String finbertModelVersion) {
        int predicted = 0;
        int failed = 0;
        for (MarketNewsEventDto fact : facts) {
            SentimentAnalysisResult baseline = finbertResults.get(fact.getId());
            if (baseline == null) {
                throw new IllegalStateException("FinBERT 基线结果缺失: eventFactId=" + fact.getId());
            }
            try {
                SentimentAnalysisResult result = mengziRuntimeService.predict(
                        inputAssembler.assemble(fact.getNormalizedTitle(), fact.getNormalizedContent()));
                predictionRepository.save(toPrediction(fact, baseline, result, finbertModelVersion));
                predicted++;
            } catch (RuntimeException exception) {
                predictionRepository.save(toFailurePrediction(
                        fact, baseline, finbertModelVersion, exception.getMessage()));
                failed++;
            }
        }
        log.info("Mengzi 影子推理批次完成: scanned={}, predicted={}, failed={}, version={}",
                facts.size(), predicted, failed, config.getModelVersion());
    }

    private void evaluatePendingPredictions() {
        List<SentimentShadowPredictionDocument> pending = predictionRepository
                .findByCandidateModelVersionAndEvaluationStatusOrderByPredictedAtAsc(
                        config.getModelVersion(), "PENDING",
                        PageRequest.of(0, Math.max(100, config.getBatchSize() * 5)));
        for (SentimentShadowPredictionDocument prediction : pending) {
            if (prediction.getPublishTime() == null || prediction.getStockCode() == null
                    || prediction.getStockCode().isBlank()) {
                prediction.setEvaluationStatus("INVALID_INPUT");
                prediction.setEvaluatedAt(LocalDateTime.now());
                predictionRepository.save(prediction);
                continue;
            }
            LocalDate publishDate = prediction.getPublishTime().toLocalDate();
            LocalDate targetDate = tradingCalendarService.nextTradingDay(publishDate);
            LocalDate exitDate = tradingCalendarService.nextTradingDay(targetDate);
            StockPriceItemDto entry = marketDataQueryService
                    .findPrice(prediction.getStockCode(), targetDate).orElse(null);
            StockPriceItemDto exit = marketDataQueryService
                    .findPrice(prediction.getStockCode(), exitDate).orElse(null);
            if (entry == null || exit == null || entry.getOpenPrice() == null
                    || exit.getOpenPrice() == null || entry.getOpenPrice().signum() <= 0) {
                continue;
            }
            double actualReturn = exit.getOpenPrice().divide(
                    entry.getOpenPrice(), 10, java.math.RoundingMode.HALF_UP).doubleValue() - 1D;
            prediction.setEvaluationTradeDate(targetDate);
            prediction.setActualReturn(actualReturn);
            prediction.setDirectionCorrect(!Boolean.TRUE.equals(prediction.getAbstained())
                    && directionMatches(prediction.getImpactScore(), actualReturn));
            prediction.setProductionDirectionCorrect(!Boolean.TRUE.equals(prediction.getProductionAbstained())
                    && directionMatches(prediction.getProductionImpactScore(), actualReturn));
            prediction.setEvaluationStatus("EVALUATED");
            prediction.setEvaluatedAt(LocalDateTime.now());
            predictionRepository.save(prediction);
        }
    }

    private void evaluateRoutingState() {
        List<SentimentShadowPredictionDocument> evaluated = predictionRepository
                .findByCandidateModelVersionAndEvaluationStatus(config.getModelVersion(), "EVALUATED");
        List<EvaluationPoint> points = aggregateEvaluationPoints(evaluated);
        long failedCount = predictionRepository
                .findByCandidateModelVersionAndEvaluationStatus(
                        config.getModelVersion(), "INFERENCE_FAILED").size();
        SentimentModelRoutingStateDocument state = routerService.currentState();
        int tradingDays = (int) points.stream().map(EvaluationPoint::tradeDate).distinct().count();
        long candidateCovered = points.stream().filter(item -> item.candidateScore() != null).count();
        long baselineCovered = points.stream().filter(item -> item.baselineScore() != null).count();
        double directionAccuracy = rate(points.stream().filter(item -> directionMatches(
                item.candidateScore(), item.actualReturn())).count(), candidateCovered);
        double baselineDirectionAccuracy = rate(points.stream().filter(item -> directionMatches(
                item.baselineScore(), item.actualReturn())).count(), baselineCovered);
        double rankIc = dailyRankIc(points, true);
        double baselineRankIc = dailyRankIc(points, false);
        double topDecileNetReturn = topDecileNetReturn(points, true);
        double baselineTopDecileNetReturn = topDecileNetReturn(points, false);
        double outputStd = standardDeviation(points.stream()
                .map(EvaluationPoint::candidateScore).filter(java.util.Objects::nonNull).toList());

        state.setShadowTradingDays(tradingDays);
        state.setEvaluatedSamples(points.size());
        state.setDirectionAccuracy(directionAccuracy);
        state.setBaselineDirectionAccuracy(baselineDirectionAccuracy);
        state.setRankIc(rankIc);
        state.setBaselineRankIc(baselineRankIc);
        state.setTopDecileNetReturn(topDecileNetReturn);
        state.setBaselineTopDecileNetReturn(baselineTopDecileNetReturn);
        state.setOutputStd(outputStd);
        state.setFailureRate(rate(failedCount, evaluated.size() + failedCount));
        state.setCoverageRate(rate(candidateCovered, points.size()));
        state.setBaselineCoverageRate(rate(baselineCovered, points.size()));
        boolean rollbackRequired = shouldRollback(evaluated);
        if (passesPromotionGate(state) && !rollbackRequired
                && !"MENGZI".equals(state.getActiveModel())) {
            state.setStatus("ELIGIBLE");
            state.setDecisionReason("Mengzi 达到影子门槛，等待人工确认激活");
        } else if ("MENGZI".equals(state.getActiveModel()) && rollbackRequired) {
            state.setPreviousModel("MENGZI");
            state.setActiveModel("FINBERT");
            state.setStatus("ROLLBACK");
            state.setDecisionReason("Mengzi 激活后滚动指标跌破自动回滚门槛");
        } else if (!"MENGZI".equals(state.getActiveModel()) && rollbackRequired) {
            state.setStatus("ROLLBACK");
            state.setDecisionReason("Mengzi 滚动指标仍未恢复，保持 FinBERT");
        } else if (!"MENGZI".equals(state.getActiveModel())) {
            state.setStatus("SHADOW");
            state.setDecisionReason("Mengzi 影子样本或指标尚未达到候选门槛");
        }
        state.setUpdatedAt(LocalDateTime.now());
        routingStateRepository.save(state);
    }

    private SentimentShadowPredictionDocument toPrediction(
            MarketNewsEventDto fact,
            SentimentAnalysisResult baseline,
            SentimentAnalysisResult result,
            String productionModelVersion) {
        SentimentShadowPredictionDocument prediction = new SentimentShadowPredictionDocument();
        prediction.setEventFactId(fact.getId());
        prediction.setCanonicalEventId(fact.getCanonicalEventId());
        prediction.setStockCode(fact.getStockCode());
        prediction.setPublishTime(fact.getPublishTime());
        prediction.setPipelineVersion(fact.getPipelineVersion());
        prediction.setProductionModelVersion(productionModelVersion);
        prediction.setProductionLabel(baseline.getLabel());
        prediction.setProductionImpactScore(baseline.getScore());
        prediction.setProductionConfidence(baseline.getConfidence());
        prediction.setProductionAbstained(baseline.isAbstained());
        prediction.setCandidateModelName(config.getModelName());
        prediction.setCandidateModelVersion(config.getModelVersion());
        prediction.setLabel(result.getLabel());
        prediction.setImpactScore(result.getScore());
        prediction.setImpactMagnitude(Math.abs(result.getScore()));
        prediction.setImpactDirection(result.getScore() > 0.05D ? "BULLISH"
                : result.getScore() < -0.05D ? "BEARISH" : "NEUTRAL");
        prediction.setConfidence(result.getConfidence());
        prediction.setCalibratedConfidence(result.getCalibratedConfidence());
        prediction.setEntropy(result.getEntropy());
        prediction.setProbabilityMargin(result.getProbabilityMargin());
        prediction.setAbstained(result.isAbstained());
        prediction.setEventType(fact.getEventType());
        prediction.setProbabilities(result.getProbabilities());
        prediction.setEvaluationStatus("PENDING");
        prediction.setPredictedAt(LocalDateTime.now());
        return prediction;
    }

    private SentimentShadowPredictionDocument toFailurePrediction(
            MarketNewsEventDto fact,
            SentimentAnalysisResult baseline,
            String productionModelVersion,
            String failureReason) {
        SentimentShadowPredictionDocument prediction = new SentimentShadowPredictionDocument();
        prediction.setEventFactId(fact.getId());
        prediction.setCanonicalEventId(fact.getCanonicalEventId());
        prediction.setStockCode(fact.getStockCode());
        prediction.setPublishTime(fact.getPublishTime());
        prediction.setPipelineVersion(fact.getPipelineVersion());
        prediction.setProductionModelVersion(productionModelVersion);
        prediction.setProductionLabel(baseline.getLabel());
        prediction.setProductionImpactScore(baseline.getScore());
        prediction.setProductionConfidence(baseline.getConfidence());
        prediction.setProductionAbstained(baseline.isAbstained());
        prediction.setCandidateModelName(config.getModelName());
        prediction.setCandidateModelVersion(config.getModelVersion());
        prediction.setEventType(fact.getEventType());
        prediction.setEvaluationStatus("INFERENCE_FAILED");
        prediction.setFailureReason(failureReason == null ? "Mengzi 推理失败" : failureReason);
        prediction.setPredictedAt(LocalDateTime.now());
        return prediction;
    }

    private void recordCycleFailure(RuntimeException exception) {
        SentimentModelRoutingStateDocument state = routerService.currentState();
        state.setFailureRate(1D);
        state.setUpdatedAt(LocalDateTime.now());
        state.setDecisionReason("Mengzi 影子周期失败: " + exception.getClass().getSimpleName());
        if ("MENGZI".equals(state.getActiveModel())) {
            state.setPreviousModel("MENGZI");
            state.setActiveModel("FINBERT");
            state.setStatus("ROLLBACK");
        } else {
            state.setStatus("BLOCKED");
        }
        routingStateRepository.save(state);
    }

    private boolean passesPromotionGate(SentimentModelRoutingStateDocument state) {
        return state.getShadowTradingDays() >= config.getMinimumShadowTradingDays()
                && state.getEvaluatedSamples() >= config.getMinimumEvaluatedSamples()
                && value(state.getRankIc()) >= config.getMinimumRankIc()
                && value(state.getRankIc()) >= value(state.getBaselineRankIc())
                && value(state.getDirectionAccuracy()) >= config.getMinimumDirectionAccuracy()
                && value(state.getDirectionAccuracy()) >= value(state.getBaselineDirectionAccuracy())
                && value(state.getTopDecileNetReturn()) >= value(state.getBaselineTopDecileNetReturn())
                + config.getMinimumTopDecileNetReturnImprovement()
                && value(state.getOutputStd()) >= config.getMinimumOutputStd()
                && value(state.getCoverageRate()) >= 0.60D
                && value(state.getFailureRate()) <= config.getMaximumFailureRate();
    }

    private boolean shouldRollback(List<SentimentShadowPredictionDocument> evaluated) {
        List<LocalDate> latestDates = evaluated.stream()
                .map(SentimentShadowPredictionDocument::getEvaluationTradeDate)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .limit(Math.max(1, config.getRollbackWindowTradingDays()))
                .toList();
        if (latestDates.size() < config.getRollbackWindowTradingDays()) {
            return false;
        }
        List<SentimentShadowPredictionDocument> window = evaluated.stream()
                .filter(item -> latestDates.contains(item.getEvaluationTradeDate()))
                .toList();
        List<EvaluationPoint> points = aggregateEvaluationPoints(window);
        if (points.size() < Math.min(config.getMinimumEvaluatedSamples(), 100)) {
            return false;
        }
        long candidateCovered = points.stream().filter(item -> item.candidateScore() != null).count();
        long baselineCovered = points.stream().filter(item -> item.baselineScore() != null).count();
        double directionAccuracy = rate(points.stream().filter(item -> directionMatches(
                item.candidateScore(), item.actualReturn())).count(), candidateCovered);
        double baselineDirectionAccuracy = rate(points.stream().filter(item -> directionMatches(
                item.baselineScore(), item.actualReturn())).count(), baselineCovered);
        double candidateRankIc = dailyRankIc(points, true);
        double baselineRankIc = dailyRankIc(points, false);
        double candidateNetReturn = topDecileNetReturn(points, true);
        double baselineNetReturn = topDecileNetReturn(points, false);
        double outputStd = standardDeviation(points.stream()
                .map(EvaluationPoint::candidateScore).filter(java.util.Objects::nonNull).toList());
        return candidateRankIc < config.getMinimumRankIc()
                || candidateRankIc < baselineRankIc
                || directionAccuracy < config.getMinimumDirectionAccuracy()
                || directionAccuracy < baselineDirectionAccuracy
                || candidateNetReturn < baselineNetReturn + config.getMinimumTopDecileNetReturnImprovement()
                || outputStd < config.getMinimumOutputStd()
                || rate(candidateCovered, points.size()) < 0.60D;
    }

    private double dailyRankIc(List<EvaluationPoint> points, boolean candidate) {
        return points.stream().collect(java.util.stream.Collectors.groupingBy(EvaluationPoint::tradeDate))
                .values().stream().mapToDouble(items -> spearman(items, candidate))
                .filter(Double::isFinite).average().orElse(0D);
    }

    private double spearman(List<EvaluationPoint> items, boolean candidate) {
        List<EvaluationPoint> valid = items.stream()
                .filter(item -> score(item, candidate) != null).toList();
        if (valid.size() < 3) {
            return Double.NaN;
        }
        List<EvaluationPoint> predictedOrder = new ArrayList<>(valid);
        predictedOrder.sort(Comparator.comparingDouble(item -> score(item, candidate)));
        List<EvaluationPoint> actualOrder = new ArrayList<>(valid);
        actualOrder.sort(Comparator.comparingDouble(EvaluationPoint::actualReturn));
        java.util.Map<String, Integer> predictedRanks = ranks(predictedOrder);
        java.util.Map<String, Integer> actualRanks = ranks(actualOrder);
        double squaredDifference = 0D;
        for (EvaluationPoint item : valid) {
            double difference = predictedRanks.get(item.stockCode()) - actualRanks.get(item.stockCode());
            squaredDifference += difference * difference;
        }
        int count = valid.size();
        return 1D - 6D * squaredDifference / (count * (count * count - 1D));
    }

    private double topDecileNetReturn(
            List<EvaluationPoint> items, boolean candidate) {
        List<EvaluationPoint> valid = items.stream()
                .filter(item -> score(item, candidate) != null)
                .sorted(Comparator.comparingDouble(
                        (EvaluationPoint item) -> score(item, candidate)).reversed())
                .toList();
        if (valid.isEmpty()) {
            return 0D;
        }
        int count = Math.max(1, (int) Math.ceil(valid.size() * 0.1D));
        double grossReturn = valid.stream().limit(count)
                .mapToDouble(EvaluationPoint::actualReturn)
                .average().orElse(0D);
        return grossReturn - roundTripCost();
    }

    private Double score(EvaluationPoint item, boolean candidate) {
        return candidate ? item.candidateScore() : item.baselineScore();
    }

    private double roundTripCost() {
        BigDecimal slippage = costProperties.getSlippageBps().multiply(BigDecimal.valueOf(2D))
                .divide(BigDecimal.valueOf(10000D), 10, RoundingMode.HALF_UP);
        return costProperties.getCommissionRate().multiply(BigDecimal.valueOf(2D))
                .add(costProperties.getTransferFeeRate().multiply(BigDecimal.valueOf(2D)))
                .add(costProperties.getStampDutyRate())
                .add(slippage)
                .add(costProperties.getCandidateSafetyMarginRate())
                .doubleValue();
    }

    private double standardDeviation(List<Double> values) {
        if (values.size() < 2) {
            return 0D;
        }
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double variance = values.stream().mapToDouble(value -> (value - mean) * (value - mean))
                .sum() / (values.size() - 1D);
        return Math.sqrt(Math.max(0D, variance));
    }

    private java.util.Map<String, Integer> ranks(List<EvaluationPoint> ordered) {
        java.util.Map<String, Integer> result = new java.util.HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            result.put(ordered.get(index).stockCode(), index + 1);
        }
        return result;
    }

    private List<EvaluationPoint> aggregateEvaluationPoints(
            List<SentimentShadowPredictionDocument> evaluated) {
        return evaluated.stream()
                .filter(item -> item.getEvaluationTradeDate() != null && item.getStockCode() != null
                        && item.getActualReturn() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        item -> item.getEvaluationTradeDate() + "|" + item.getStockCode(),
                        LinkedHashMap::new, java.util.stream.Collectors.toList()))
                .values().stream().map(group -> {
                    SentimentShadowPredictionDocument first = group.get(0);
                    Double candidateScore = averageScore(group, true);
                    Double baselineScore = averageScore(group, false);
                    double actualReturn = group.stream()
                            .map(SentimentShadowPredictionDocument::getActualReturn)
                            .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue)
                            .average().orElse(0D);
                    return new EvaluationPoint(first.getEvaluationTradeDate(), first.getStockCode(),
                            candidateScore, baselineScore, actualReturn);
                }).toList();
    }

    private Double averageScore(List<SentimentShadowPredictionDocument> group, boolean candidate) {
        java.util.OptionalDouble average = group.stream()
                .filter(item -> candidate ? !Boolean.TRUE.equals(item.getAbstained())
                        : !Boolean.TRUE.equals(item.getProductionAbstained()))
                .map(item -> candidate ? item.getImpactScore() : item.getProductionImpactScore())
                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average();
        return average.isPresent() ? average.getAsDouble() : null;
    }

    private boolean directionMatches(Double predicted, double actual) {
        if (predicted == null || Math.abs(predicted) <= 0.05D || Math.abs(actual) <= 0.001D) {
            return false;
        }
        return Math.signum(predicted) == Math.signum(actual);
    }

    private double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0D : numerator / (double) denominator;
    }

    private double value(Double value) {
        return value == null || !Double.isFinite(value) ? 0D : value;
    }

    private record EvaluationPoint(
            LocalDate tradeDate,
            String stockCode,
            Double candidateScore,
            Double baselineScore,
            double actualReturn) {
    }
}
// AI_GENERATE_END --------------
