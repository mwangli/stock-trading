// AI_GENERATE_START-----
package com.stock.model.service;

import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.service.MarketDataQueryService;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型预测评估服务。
 *
 * 负责回填目标交易日行情、计算单批次能力指标，并维护滚动窗口交易门禁结论。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
class PredictionEvaluationService {

    private static final int MINIMUM_ABILITY_SAMPLE_COUNT = 100;
    private static final int MINIMUM_CALIBRATION_SAMPLE_COUNT = 100;
    private static final double MIN_DIRECTION_PROBABILITY_STD = 0.005D;
    private static final double MIN_PREDICTED_RETURN_STD = 0.0005D;
    private static final double MIN_BASELINE_ACCURACY_LIFT = 0.02D;
    private static final int ROLLING_WINDOW_BATCHES = 20;
    private static final int MINIMUM_ROLLING_SAMPLE_COUNT = 5000;
    private static final List<String> RUNNING_STATUSES = List.of("QUEUED", "RUNNING");
    private static final String HISTORICAL_BACKFILL_TRIGGER = "scheduler-backfill";
    private static final String MANUAL_BACKFILL_TRIGGER_PREFIX = "manual-backfill:";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final MarketDataQueryService marketDataQueryService;
    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;
    private final MongoTemplate mongoTemplate;
    private final PredictionMetricsCalculator metricsCalculator;

    ModelPredictionBatchDocument evaluateBatch(ModelPredictionBatchDocument batch) {
        if (hasStatus(batch, RUNNING_STATUSES)) {
            throw new IllegalStateException("预测批次尚未完成，暂不能评估");
        }
        List<ModelPredictionDocument> predictions = predictionRepository.findByBatchId(batch.getId());
        if (predictions.isEmpty()) {
            throw new IllegalStateException("预测批次没有可评估明细");
        }

        LocalDate today = LocalDate.now(MARKET_ZONE);
        LocalDate latestMarketDate = marketDataQueryService.findLatestMarketDate();
        Map<LocalDate, List<ModelPredictionDocument>> byTargetDate = predictions.stream()
                .filter(item -> item.getTargetTradeDate() != null)
                .collect(Collectors.groupingBy(ModelPredictionDocument::getTargetTradeDate));
        LocalDateTime evaluatedAt = LocalDateTime.now();
        List<ModelPredictionDocument> changed = new ArrayList<>();
        int missingMarketDataCount = 0;
        for (Map.Entry<LocalDate, List<ModelPredictionDocument>> entry : byTargetDate.entrySet()) {
            if (entry.getKey().isAfter(today)) {
                log.info("跳过尚未到达目标交易日的预测评估: batchId={}, targetTradeDate={}, today={}",
                        batch.getId(), entry.getKey(), today);
                continue;
            }
            List<String> codes = entry.getValue().stream().map(ModelPredictionDocument::getStockCode).toList();
            Map<String, StockPriceItemDto> actualPrices = marketDataQueryService.findPrices(codes, entry.getKey()).stream()
                    .collect(Collectors.toMap(StockPriceItemDto::getCode, Function.identity(), (left, right) -> left));
            for (ModelPredictionDocument prediction : entry.getValue()) {
                StockPriceItemDto actualPrice = actualPrices.get(prediction.getStockCode());
                if (actualPrice != null && isPositive(actualPrice.getClosePrice())
                        && prediction.getBaseClosePrice() != null && prediction.getBaseClosePrice() > 0D) {
                    double actualClose = actualPrice.getClosePrice().doubleValue();
                    double actualReturn = actualClose / prediction.getBaseClosePrice() - 1D;
                    double error = prediction.getPredictedReturn() - actualReturn;
                    prediction.setActualClosePrice(actualClose);
                    prediction.setActualReturn(actualReturn);
                    prediction.setReturnError(error);
                    prediction.setAbsoluteError(Math.abs(error));
                    prediction.setSquaredError(error * error);
                    boolean predictedUp = prediction.getDirectionProbability() != null
                            && prediction.getDirectionProbability() >= 0.5D;
                    prediction.setDirectionCorrect(predictedUp == (actualReturn > 0D));
                    prediction.setEvaluationStatus("EVALUATED");
                    prediction.setEvaluatedAt(evaluatedAt);
                    changed.add(prediction);
                } else {
                    missingMarketDataCount++;
                }
            }
        }
        if (!changed.isEmpty()) {
            predictionRepository.saveAll(changed);
        }
        refreshAbility(batch, predictionRepository.findByBatchId(batch.getId()));
        ModelPredictionBatchDocument refreshed = batchRepository.findById(batch.getId()).orElseThrow();
        log.info("预测实际结果刷新完成: batchId={}, targetTradeDate={}, today={}, latestMarketDate={}, "
                        + "evaluated={}, pendingMarketData={}, noMarketData={}, ability={}",
                refreshed.getId(), batch.getTargetTradeDate(), today, latestMarketDate,
                refreshed.getEvaluatedCount(), missingMarketDataCount,
                refreshed.getNoMarketDataCount(), refreshed.getAbilityLevel());
        if (missingMarketDataCount > 0) {
            log.warn("预测批次仍有明细等待目标日行情: batchId={}, missingMarketData={}, latestMarketDate={}",
                    refreshed.getId(), missingMarketDataCount, latestMarketDate);
        }
        return refreshed;
    }

    void refreshAbility(ModelPredictionBatchDocument batch, List<ModelPredictionDocument> allPredictions) {
        updateCrossSectionalPercentiles(allPredictions);
        List<ModelPredictionDocument> evaluated = allPredictions.stream()
                .filter(item -> "EVALUATED".equals(item.getEvaluationStatus()))
                .filter(item -> item.getPredictedReturn() != null && Double.isFinite(item.getPredictedReturn()))
                .filter(item -> item.getActualReturn() != null && Double.isFinite(item.getActualReturn()))
                .toList();
        int noMarketDataCount = (int) allPredictions.stream()
                .filter(item -> "NO_MARKET_DATA".equals(item.getEvaluationStatus())).count();
        batch.setEvaluatedCount(evaluated.size());
        batch.setNoMarketDataCount(noMarketDataCount);
        batch.setEvaluationRate(rate(evaluated.size(), Math.max(1, batch.getSuccessCount())));
        if (evaluated.isEmpty()) {
            batch.setDirectionAuc(null);
            batch.setBrierScore(null);
            batch.setBrierBaseline(null);
            batch.setExpectedCalibrationError(null);
            batch.setCalibrationSampleCount(0);
            batch.setAbilityLevel("INSUFFICIENT_DATA");
            batch.setIterationRecommended(false);
            batch.setAbilityMessage("目标交易日行情尚未完整形成，需继续等待实际结果");
            batch.setTradingEligibility("DISABLED");
            batch.setTradingEligibilityReason("尚无可评估的样本，禁止进入买入流程");
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            return;
        }

        double directionAccuracy = evaluated.stream()
                .filter(item -> Boolean.TRUE.equals(item.getDirectionCorrect())).count() / (double) evaluated.size();
        double mae = evaluated.stream().mapToDouble(ModelPredictionDocument::getAbsoluteError).average().orElse(0D);
        double rmse = Math.sqrt(evaluated.stream().mapToDouble(ModelPredictionDocument::getSquaredError)
                .average().orElse(0D));
        double[] predicted = evaluated.stream().mapToDouble(ModelPredictionDocument::getPredictedReturn).toArray();
        double[] actual = evaluated.stream().mapToDouble(ModelPredictionDocument::getActualReturn).toArray();
        List<ModelPredictionDocument> withProbability = evaluated.stream()
                .filter(item -> item.getDirectionProbability() != null
                        && Double.isFinite(item.getDirectionProbability())
                        && item.getDirectionProbability() >= 0D
                        && item.getDirectionProbability() <= 1D)
                .toList();
        double actualUpRate = evaluated.stream().filter(item -> item.getActualReturn() > 0D).count()
                / (double) evaluated.size();
        double constantBaselineAccuracy = Math.max(actualUpRate, 1D - actualUpRate);
        List<ModelPredictionDocument> calibrationSamples = withProbability;
        Double directionAuc = metricsCalculator.areaUnderRoc(calibrationSamples);
        Double brierScore = metricsCalculator.brierScore(calibrationSamples);
        double calibrationUpRate = calibrationSamples.stream().filter(item -> item.getActualReturn() > 0D).count()
                / (double) Math.max(1, calibrationSamples.size());
        Double brierBaseline = calibrationSamples.isEmpty() ? null
                : calibrationUpRate * (1D - calibrationUpRate);
        Double expectedCalibrationError = metricsCalculator.expectedCalibrationError(calibrationSamples);
        double directionProbabilityStd = metricsCalculator.standardDeviation(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).toArray());
        double predictedReturnStd = metricsCalculator.standardDeviation(predicted);
        double pearson = metricsCalculator.correlation(predicted, actual);
        double spearman = metricsCalculator.correlation(metricsCalculator.rank(predicted), metricsCalculator.rank(actual));
        List<ModelPredictionDocument> sorted = evaluated.stream()
                .sorted(Comparator.comparing(ModelPredictionDocument::getPredictedReturn).reversed())
                .toList();
        int topCount = Math.max(1, (int) Math.ceil(sorted.size() * 0.1D));
        List<ModelPredictionDocument> top = sorted.subList(0, topCount);
        double topAverageReturn = top.stream().mapToDouble(ModelPredictionDocument::getActualReturn).average().orElse(0D);
        double topDirectionAccuracy = top.stream().filter(item -> item.getActualReturn() > 0D).count()
                / (double) top.size();
        List<ModelPredictionDocument> highConfidence = evaluated.stream()
                .filter(item -> item.getDirectionProbability() != null
                        && (item.getDirectionProbability() >= 0.65D || item.getDirectionProbability() <= 0.35D))
                .toList();
        Double highConfidenceAccuracy = highConfidence.isEmpty() ? null
                : highConfidence.stream().filter(item -> Boolean.TRUE.equals(item.getDirectionCorrect())).count()
                / (double) highConfidence.size();
        List<ModelPredictionDocument> highRisk = evaluated.stream()
                .filter(item -> item.getDownsideRisk() != null && item.getDownsideRisk() >= 0.60D)
                .toList();
        Double downsideHitRate = highRisk.isEmpty() ? null
                : highRisk.stream().filter(item -> item.getActualReturn() < 0D).count() / (double) highRisk.size();

        String abilityLevel;
        String abilityMessage;
        if (evaluated.size() < MINIMUM_ABILITY_SAMPLE_COUNT) {
            abilityLevel = "INSUFFICIENT_DATA";
            abilityMessage = "已评估样本不足 100 条，当前结果只用于链路验证";
        } else if (directionAccuracy >= 0.55D && spearman >= 0.05D && mae <= 0.03D
                && topAverageReturn > 0D) {
            abilityLevel = "STRONG";
            abilityMessage = "方向、排序、误差和高分股票表现达到当前工程基线";
        } else if (directionAccuracy >= 0.50D && spearman >= 0D && mae <= 0.05D) {
            abilityLevel = "ACCEPTABLE";
            abilityMessage = "模型达到最低可用基线，仍需积累多交易日样本";
        } else {
            abilityLevel = "WEAK";
            abilityMessage = "核心预测指标低于基线，建议检查数据周期、特征和训练参数";
        }

        boolean collapsed = directionProbabilityStd < MIN_DIRECTION_PROBABILITY_STD
                || predictedReturnStd < MIN_PREDICTED_RETURN_STD;
        boolean beatsBaseline = directionAccuracy >= constantBaselineAccuracy + MIN_BASELINE_ACCURACY_LIFT;
        String tradingEligibility = "OBSERVE_ONLY";
        String tradingEligibilityReason;
        if (evaluated.size() < MINIMUM_ABILITY_SAMPLE_COUNT
                || calibrationSamples.size() < MINIMUM_CALIBRATION_SAMPLE_COUNT) {
            tradingEligibility = "DISABLED";
            tradingEligibilityReason = "有效回填或概率校准样本不足，不能证明模型具备交易能力";
        } else if (collapsed) {
            tradingEligibilityReason = "模型输出区分度不足：上涨概率或预测收益标准差过低";
        } else if (!beatsBaseline || spearman <= 0D || topAverageReturn <= 0D) {
            tradingEligibilityReason = "模型未稳定超过多数方向基线，或 Top 10% 排序收益未为正";
        } else {
            tradingEligibilityReason = "单个交易日不能授予真实交易资格，需通过滚动窗口和模拟交易门禁";
        }

        batch.setDirectionAccuracy(directionAccuracy);
        batch.setActualUpRate(actualUpRate);
        batch.setConstantBaselineAccuracy(constantBaselineAccuracy);
        batch.setDirectionAuc(directionAuc);
        batch.setBrierScore(brierScore);
        batch.setBrierBaseline(brierBaseline);
        batch.setExpectedCalibrationError(expectedCalibrationError);
        batch.setCalibrationSampleCount(calibrationSamples.size());
        batch.setDirectionProbabilityMean(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).average().orElse(0D));
        batch.setDirectionProbabilityStd(directionProbabilityStd);
        batch.setDirectionProbabilityMin(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).min().orElse(0D));
        batch.setDirectionProbabilityMax(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).max().orElse(0D));
        batch.setPredictedReturnStd(predictedReturnStd);
        batch.setReturnMae(mae);
        batch.setReturnRmse(rmse);
        batch.setPearsonIc(pearson);
        batch.setSpearmanRankIc(spearman);
        batch.setTopDecileAverageReturn(topAverageReturn);
        batch.setTopDecileDirectionAccuracy(topDirectionAccuracy);
        batch.setHighConfidenceAccuracy(highConfidenceAccuracy);
        batch.setDownsideRiskHitRate(downsideHitRate);
        batch.setAbilityLevel(abilityLevel);
        batch.setIterationRecommended("WEAK".equals(abilityLevel));
        batch.setAbilityMessage(abilityMessage + "；交易门禁：" + tradingEligibilityReason);
        batch.setTradingEligibility(tradingEligibility);
        batch.setTradingEligibilityReason(tradingEligibilityReason);
        refreshRollingAssessment(batch);
        long pendingCount = allPredictions.stream()
                .filter(item -> "PENDING".equals(item.getEvaluationStatus())).count();
        if (pendingCount == 0 && !hasStatus(batch, RUNNING_STATUSES)) {
            batch.setStatus("EVALUATED");
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
    }

    void resetPredictionEvaluationDetails(String batchId) {
        Query query = Query.query(Criteria.where("batchId").is(batchId));
        Update update = new Update()
                .set("evaluationStatus", "PENDING")
                .unset("actualClosePrice")
                .unset("actualReturn")
                .unset("returnError")
                .unset("absoluteError")
                .unset("squaredError")
                .unset("directionCorrect")
                .unset("evaluatedAt");
        mongoTemplate.updateMulti(query, update, ModelPredictionDocument.class);
    }

    void resetEvaluationSummary(ModelPredictionBatchDocument batch, String abilityMessage) {
        batch.setEvaluatedCount(0);
        batch.setNoMarketDataCount(0);
        batch.setEvaluationRate(null);
        batch.setDirectionAccuracy(null);
        batch.setActualUpRate(null);
        batch.setConstantBaselineAccuracy(null);
        batch.setDirectionAuc(null);
        batch.setBrierScore(null);
        batch.setBrierBaseline(null);
        batch.setExpectedCalibrationError(null);
        batch.setCalibrationSampleCount(0);
        batch.setDirectionProbabilityMean(null);
        batch.setDirectionProbabilityStd(null);
        batch.setDirectionProbabilityMin(null);
        batch.setDirectionProbabilityMax(null);
        batch.setPredictedReturnStd(null);
        batch.setRollingWindowBatchCount(null);
        batch.setRollingWindowSampleCount(null);
        batch.setRollingAverageSpearmanRankIc(null);
        batch.setRollingAverageTopDecileReturn(null);
        batch.setReturnMae(null);
        batch.setReturnRmse(null);
        batch.setPearsonIc(null);
        batch.setSpearmanRankIc(null);
        batch.setTopDecileAverageReturn(null);
        batch.setTopDecileDirectionAccuracy(null);
        batch.setHighConfidenceAccuracy(null);
        batch.setDownsideRiskHitRate(null);
        batch.setAbilityLevel("INSUFFICIENT_DATA");
        batch.setIterationRecommended(false);
        batch.setAbilityMessage(abilityMessage);
        batch.setTradingEligibility("DISABLED");
        batch.setTradingEligibilityReason("评估尚未完成，禁止进入买入流程");
    }

    private void refreshRollingAssessment(ModelPredictionBatchDocument batch) {
        if (isHistoricalReconstructionBatch(batch)) {
            batch.setRollingWindowBatchCount(0);
            batch.setRollingWindowSampleCount(0);
            batch.setRollingAverageSpearmanRankIc(null);
            batch.setRollingAverageTopDecileReturn(null);
            batch.setTradingEligibility("DISABLED");
            batch.setTradingEligibilityReason("历史补缺批次使用当前模型重建，仅用于数据分析，不参与真实交易门禁");
            return;
        }
        List<ModelPredictionBatchDocument> history = batchRepository
                .findByModelIdAndStatusInOrderByTargetTradeDateDesc(
                        batch.getModelId(), List.of("COMPLETED", "PARTIAL", "EVALUATED"))
                .stream()
                .filter(item -> !isHistoricalReconstructionBatch(item))
                .filter(item -> item.getEvaluatedCount() >= MINIMUM_ABILITY_SAMPLE_COUNT)
                .limit(ROLLING_WINDOW_BATCHES)
                .collect(Collectors.toCollection(ArrayList::new));
        if (history.stream().noneMatch(item -> batch.getId().equals(item.getId()))) {
            history.add(0, batch);
        }
        history = history.stream().limit(ROLLING_WINDOW_BATCHES).toList();
        int sampleCount = history.stream().mapToInt(ModelPredictionBatchDocument::getEvaluatedCount).sum();
        double averageSpearman = history.stream().map(ModelPredictionBatchDocument::getSpearmanRankIc)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D);
        double averageTopReturn = history.stream().map(ModelPredictionBatchDocument::getTopDecileAverageReturn)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D);
        batch.setRollingWindowBatchCount(history.size());
        batch.setRollingWindowSampleCount(sampleCount);
        batch.setRollingAverageSpearmanRankIc(averageSpearman);
        batch.setRollingAverageTopDecileReturn(averageTopReturn);
        if (!"DISABLED".equals(batch.getTradingEligibility())) {
            batch.setTradingEligibility("OBSERVE_ONLY");
            batch.setTradingEligibilityReason("滚动窗口 " + history.size() + "/" + ROLLING_WINDOW_BATCHES
                    + " 个批次、" + sampleCount + "/" + MINIMUM_ROLLING_SAMPLE_COUNT
                    + " 个样本；且需完成模拟交易验证后才能开放买入");
        }
    }

    private void updateCrossSectionalPercentiles(List<ModelPredictionDocument> predictions) {
        List<ModelPredictionDocument> returnRankable = predictions.stream()
                .filter(item -> item.getPredictedReturn() != null && Double.isFinite(item.getPredictedReturn()))
                .toList();
        List<ModelPredictionDocument> probabilityRankable = predictions.stream()
                .filter(item -> item.getDirectionProbability() != null
                        && Double.isFinite(item.getDirectionProbability()))
                .toList();
        List<ModelPredictionDocument> scoreRankable = predictions.stream()
                .filter(item -> item.getRankScore() != null && Double.isFinite(item.getRankScore()))
                .toList();
        assignPercentiles(returnRankable, true);
        assignPercentiles(probabilityRankable, false);
        assignRankScorePercentiles(scoreRankable);
        if (!returnRankable.isEmpty() || !probabilityRankable.isEmpty() || !scoreRankable.isEmpty()) {
            predictionRepository.saveAll(predictions);
        }
    }

    private boolean isHistoricalReconstructionBatch(ModelPredictionBatchDocument batch) {
        if (batch == null) {
            return false;
        }
        if (batch.getRerunSourceBatchId() != null && !batch.getRerunSourceBatchId().isBlank()) {
            return true;
        }
        if (batch.getTriggeredBy() == null) {
            return false;
        }
        return HISTORICAL_BACKFILL_TRIGGER.equals(batch.getTriggeredBy())
                || batch.getTriggeredBy().startsWith(MANUAL_BACKFILL_TRIGGER_PREFIX);
    }

    private void assignPercentiles(List<ModelPredictionDocument> items, boolean returnMetric) {
        if (items.isEmpty()) {
            return;
        }
        List<ModelPredictionDocument> sorted = items.stream()
                .sorted(Comparator.comparingDouble(item -> returnMetric
                        ? item.getPredictedReturn() : item.getDirectionProbability()))
                .toList();
        int denominator = Math.max(1, sorted.size() - 1);
        for (int index = 0; index < sorted.size(); index++) {
            double percentile = index / (double) denominator;
            if (returnMetric) {
                sorted.get(index).setPredictedReturnPercentile(percentile);
            } else {
                sorted.get(index).setDirectionProbabilityPercentile(percentile);
            }
        }
    }

    private void assignRankScorePercentiles(List<ModelPredictionDocument> items) {
        if (items.isEmpty()) {
            return;
        }
        List<ModelPredictionDocument> sorted = items.stream()
                .sorted(Comparator.comparingDouble(ModelPredictionDocument::getRankScore))
                .toList();
        int denominator = Math.max(1, sorted.size() - 1);
        for (int index = 0; index < sorted.size(); index++) {
            sorted.get(index).setCrossSectionPercentile(index / (double) denominator);
        }
    }

    private boolean hasStatus(ModelPredictionBatchDocument batch, List<String> statuses) {
        return batch != null && batch.getStatus() != null && statuses.contains(batch.getStatus());
    }

    private Double rate(int numerator, int denominator) {
        return denominator <= 0 ? 0D : numerator / (double) denominator;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
// AI_GENERATE_END-----
