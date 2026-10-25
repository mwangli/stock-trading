// AI_GENERATE_START --------------------
package com.stock.model.service;

import com.stock.model.algorithm.TemporalModelOutputCalculator;
import com.stock.model.algorithm.TemporalModelWeightOptimizer;
import com.stock.model.algorithm.TemporalModelWeightOptimizer.CombinationMetrics;
import com.stock.model.algorithm.TemporalModelWeightOptimizer.EvaluationSample;
import com.stock.model.algorithm.TemporalModelWeightOptimizer.OptimizationResult;
import com.stock.model.config.PredictionCostProperties;
import com.stock.model.config.TemporalEnsembleConfig;
import com.stock.model.dto.PredictionCandidateBatchDto;
import com.stock.model.dto.PredictionCandidateItemDto;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.entity.TemporalModelEnsembleStateDocument;
import com.stock.model.entity.TemporalModelShadowPredictionDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelPredictionRepository;
import com.stock.model.repository.TemporalModelEnsembleStateRepository;
import com.stock.model.repository.TemporalModelShadowPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ranking LSTM 与 MASTER 的四任务融合、连续权重搜索和自动选择服务。
 *
 * @see <a href="http://localhost:5173/docs/backend/model/designs/temporal-model-ensemble">
 *     双模型连续权重融合详细设计</a>
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemporalModelEnsembleService {

    /** 全局时序模型融合状态主键。 */
    public static final String ENSEMBLE_STATE_ID = "GLOBAL_TEMPORAL_ENSEMBLE";

    /** 当前融合状态结构版本。 */
    public static final String SCHEMA_VERSION = "v2-dual-weight";

    private static final String STATUS_OBSERVING = "OBSERVING";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DEGRADED = "DEGRADED";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String MODEL_NAME = LstmTrainerService.DEFAULT_SHARED_MODEL_NAME;

    private final TemporalEnsembleConfig config;
    private final PredictionCostProperties costProperties;
    private final TemporalModelShadowPredictionRepository shadowRepository;
    private final TemporalModelEnsembleStateRepository stateRepository;
    private final ModelPredictionRepository predictionRepository;
    private final LstmModelRepository modelRepository;
    private final TemporalModelOutputCalculator outputCalculator;
    private final TemporalModelWeightOptimizer weightOptimizer;
    private final ModelOperationAuditService auditService;

    /**
     * 注册新的 MASTER 候选并重置双模型评估窗口。
     *
     * @param modelId MASTER 候选模型文档 ID
     */
    public synchronized void registerMasterCandidate(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("MASTER 候选模型 ID 不能为空");
        }
        LstmModelDocument candidate = modelRepository.findById(modelId)
                .orElseThrow(() -> new IllegalArgumentException("MASTER 候选模型不存在: " + modelId));
        TemporalModelEnsembleStateDocument state = currentState();
        if (state.getMasterModelId() != null && !state.getMasterModelId().equals(modelId)) {
            retireMaster(state.getMasterModelId());
        }
        candidate.setStatus("CANDIDATE");
        modelRepository.save(candidate);
        state.setMasterModelId(modelId);
        resetState(state, state.getRankingLstmModelId(), modelId,
                "MASTER 候选版本变化，已清空旧组合窗口并重新观察");
    }

    /**
     * 判断自动训练链是否需要生成新的 MASTER 挑战者。
     *
     * @param baselineModelId 新晋级的 Ranking LSTM 模型文档 ID
     * @return 当前没有同特征契约 MASTER 候选时返回 true
     */
    public synchronized boolean shouldTrainMasterCandidate(String baselineModelId) {
        TemporalModelEnsembleStateDocument state = currentState();
        if (!Objects.equals(state.getRankingLstmModelId(), baselineModelId)) {
            resetState(state, baselineModelId, state.getMasterModelId(),
                    "Ranking LSTM 版本变化，已清空旧组合窗口并重新观察");
        }
        if (state.getMasterModelId() == null || state.getMasterModelId().isBlank()) {
            return true;
        }
        LstmModelDocument baseline = modelRepository.findById(baselineModelId).orElse(null);
        LstmModelDocument master = modelRepository.findById(state.getMasterModelId()).orElse(null);
        if (baseline != null && master != null
                && Objects.equals(baseline.getFeatureVersion(), master.getFeatureVersion())) {
            return false;
        }
        retireMaster(state.getMasterModelId());
        resetState(state, baselineModelId, null,
                "基础模型特征契约变化，旧 MASTER 已退役并等待自动重训");
        return true;
    }

    /**
     * 将 Ranking LSTM 基础批次转换为双模型生产有效输出。
     *
     * @param baseline Ranking LSTM 股票池和日期载体
     * @return 已写入批次权重、融合状态和四任务有效输出的候选批次
     * @throws IllegalStateException 两个模型都无法提供合法四任务输出时抛出
     */
    public synchronized PredictionCandidateBatchDto apply(PredictionCandidateBatchDto baseline) {
        if (baseline == null || baseline.getItems() == null || baseline.getItems().isEmpty()) {
            throw new IllegalStateException("预测候选批次为空，无法生成生产有效输出");
        }
        TemporalModelEnsembleStateDocument state = currentState();
        if (!Objects.equals(state.getRankingLstmModelId(), baseline.getModelId())) {
            resetState(state, baseline.getModelId(), state.getMasterModelId(),
                    "Ranking LSTM 版本变化，权重已重置并重新积累样本外证据");
        }
        Map<String, TemporalModelShadowPredictionDocument> masterOutputs = loadMasterOutputs(
                baseline.getBatchId(), state.getMasterModelId());
        boolean lstmAvailable = baseline.getItems().stream()
                .allMatch(item -> outputCalculator.isValid(toLstmOutput(item)));
        double masterCoverage = (double) masterOutputs.size() / baseline.getItems().size();
        boolean masterAvailable = state.getMasterModelId() != null
                && masterCoverage >= config.getMinimumCoverageRate()
                && baseline.getItems().stream().allMatch(item -> outputCalculator.isValid(
                toMasterOutput(masterOutputs.get(item.getStockCode()))));

        double lstmWeight;
        double masterWeight;
        String batchStatus = state.getStatus();
        if (!config.isProductionBlendEnabled()) {
            if (!lstmAvailable) {
                throw unavailable(state, "生产融合配置关闭且 Ranking LSTM 输出无效");
            }
            lstmWeight = 1D;
            masterWeight = 0D;
            batchStatus = STATUS_ACTIVE;
        } else if (lstmAvailable && masterAvailable) {
            lstmWeight = state.getRankingLstmWeight();
            masterWeight = state.getMasterWeight();
        } else if (lstmAvailable && (state.getMasterModelId() == null
                || state.getMasterModelId().isBlank())) {
            lstmWeight = 1D;
            masterWeight = 0D;
            batchStatus = STATUS_OBSERVING;
        } else if (lstmAvailable) {
            lstmWeight = 1D;
            masterWeight = 0D;
            batchStatus = STATUS_DEGRADED;
            recordRuntimeDegradation(state, "MASTER 制品、覆盖率或四任务输出不可用，本批次临时仅使用 Ranking LSTM");
        } else if (masterAvailable) {
            lstmWeight = 0D;
            masterWeight = 1D;
            batchStatus = STATUS_DEGRADED;
            recordRuntimeDegradation(state, "Ranking LSTM 四任务输出不可用，本批次临时仅使用 MASTER");
        } else {
            throw unavailable(state, "Ranking LSTM 与 MASTER 均无法形成合法生产输出");
        }

        List<TemporalModelOutputCalculator.EffectiveOutput> effectiveOutputs = outputCalculator.calculateDaily(
                baseline.getItems().stream().map(item -> new TemporalModelOutputCalculator.WeightedOutput(
                        item.getStockCode(), toLstmOutput(item),
                        toMasterOutput(masterOutputs.get(item.getStockCode())))).toList(),
                lstmWeight, masterWeight);
        Map<String, TemporalModelOutputCalculator.EffectiveOutput> byStock = effectiveOutputs.stream()
                .collect(Collectors.toMap(TemporalModelOutputCalculator.EffectiveOutput::stockCode,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        for (PredictionCandidateItemDto item : baseline.getItems()) {
            TemporalModelOutputCalculator.EffectiveOutput output = byStock.get(item.getStockCode());
            item.setEffectiveRankScore(output.rankScore());
            item.setEffectiveExpectedExcessReturn(output.expectedExcessReturn());
            item.setEffectiveUpProbability(output.upProbability());
            item.setEffectiveUpProbabilityPercentile(output.upProbabilityPercentile());
            item.setEffectiveDownsideQuantile(output.downsideQuantile());
            item.setEffectiveModelScore(output.modelScore());
            item.setEffectiveEstimatedNetReturn(estimateNetReturn(output.expectedExcessReturn()));
        }
        baseline.setRankingLstmModelId(baseline.getModelId());
        baseline.setMasterModelId(state.getMasterModelId());
        baseline.setRankingLstmWeight(lstmWeight);
        baseline.setMasterWeight(masterWeight);
        baseline.setWeightVersion(state.getWeightVersion());
        baseline.setEnsembleStatus(batchStatus);
        baseline.setNewsFeaturesEmbedded(newsFeaturesEmbedded(baseline.getModelId(), state.getMasterModelId(),
                masterWeight));
        return baseline;
    }

    /**
     * 根据最近样本外窗口刷新连续权重、目标权重和自动选择状态。
     */
    public synchronized void refreshState() {
        TemporalModelEnsembleStateDocument state = currentState();
        String masterModelId = state.getMasterModelId();
        if (masterModelId == null || masterModelId.isBlank()) {
            observe(state, "当前没有 MASTER 候选，等待自动训练", 0, 0);
            return;
        }
        int pageSize = Math.max(1000, config.getRollingWindowTradingDays() * 6000);
        List<TemporalModelShadowPredictionDocument> all = shadowRepository
                .findByModelIdAndEvaluationStatusOrderByTargetTradeDateDesc(
                        masterModelId, "EVALUATED", PageRequest.of(0, pageSize));
        List<LocalDate> latestDates = all.stream()
                .map(TemporalModelShadowPredictionDocument::getTargetTradeDate)
                .filter(Objects::nonNull)
                .distinct()
                .limit(Math.max(1, config.getRollingWindowTradingDays()))
                .toList();
        List<TemporalModelShadowPredictionDocument> windowShadows = all.stream()
                .filter(item -> latestDates.contains(item.getTargetTradeDate()))
                .toList();
        List<String> batchIds = windowShadows.stream()
                .map(TemporalModelShadowPredictionDocument::getBaselineBatchId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        List<ModelPredictionDocument> baselineDocuments = predictionRepository.findByBatchIdIn(batchIds);
        String rankingLstmModelId = resolveRankingLstmModelId(state, windowShadows, baselineDocuments);
        if (rankingLstmModelId == null) {
            observe(state, "尚无可识别的 Ranking LSTM 样本外基线", 0, 0);
            return;
        }
        if (!Objects.equals(state.getRankingLstmModelId(), rankingLstmModelId)) {
            resetState(state, rankingLstmModelId, masterModelId,
                    "Ranking LSTM 版本变化，已清空旧组合评估窗口");
            return;
        }
        List<ModelPredictionDocument> currentBaselines = baselineDocuments.stream()
                .filter(item -> rankingLstmModelId.equals(item.getModelId()))
                .toList();
        Map<String, ModelPredictionDocument> baselines = currentBaselines.stream()
                .filter(item -> item.getActualReturn() != null && Double.isFinite(item.getActualReturn()))
                .collect(Collectors.toMap(item -> key(item.getBatchId(), item.getStockCode()),
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, Long> baselineCounts = currentBaselines.stream().collect(Collectors.groupingBy(
                ModelPredictionDocument::getBatchId, LinkedHashMap::new, Collectors.counting()));
        List<EvaluationSample> windowSamples = windowShadows.stream()
                .map(item -> toEvaluationSample(item,
                        baselines.get(key(item.getBaselineBatchId(), item.getStockCode()))))
                .filter(Objects::nonNull)
                .toList();
        Map<LocalDate, Long> validSampleCounts = windowSamples.stream().collect(Collectors.groupingBy(
                EvaluationSample::date, LinkedHashMap::new, Collectors.counting()));
        List<LocalDate> coveredDates = latestDates.stream()
                .filter(date -> validSampleCounts.getOrDefault(date, 0L) >= 3L)
                .filter(date -> coverageRate(date, windowShadows, baselineCounts,
                        validSampleCounts.getOrDefault(date, 0L)) >= config.getMinimumCoverageRate())
                .toList();
        List<EvaluationSample> samples = windowSamples.stream()
                .filter(item -> coveredDates.contains(item.date()))
                .toList();
        if (coveredDates.size() < config.getMinimumEvaluatedTradingDays()) {
            observe(state, "满足覆盖率的评估交易日不足", coveredDates.size(), samples.size());
            return;
        }
        if (samples.size() < config.getMinimumEvaluatedSamples()) {
            observe(state, "双模型有效评估样本不足", coveredDates.size(), samples.size());
            return;
        }

        OptimizationResult optimization = weightOptimizer.optimize(samples, state.getMasterWeight());
        updateSingleModelMetrics(state, weightOptimizer.evaluate(samples, 0D),
                weightOptimizer.evaluate(samples, 1D));
        state.setEvaluatedTradingDays(coveredDates.size());
        state.setEvaluatedSamples(samples.size());
        state.setUpdatedAt(LocalDateTime.now());
        CombinationMetrics best = optimization.best();
        if (best == null) {
            degradeWithoutChangingWeight(state, "所有双模型权重组合均未通过绩效门禁，继续沿用上一版权重");
            return;
        }

        CombinationMetrics current = optimization.current();
        Double currentScore = current != null && current.eligible() ? current.qualityScore() : null;
        double improvement = currentScore == null
                ? Double.POSITIVE_INFINITY : best.qualityScore() - currentScore;
        boolean shouldAdjust = weightOptimizer.shouldAdjust(current, best);
        double oldMasterWeight = state.getMasterWeight();
        double targetMasterWeight = shouldAdjust ? best.masterWeight() : oldMasterWeight;
        CombinationMetrics next = shouldAdjust
                ? weightOptimizer.nextEligibleCombination(oldMasterWeight, best,
                optimization.eligibleCombinations())
                : current;
        if (shouldAdjust && next == null) {
            degradeForUnsafeWeightStep(state, best, currentScore);
            return;
        }
        double nextMasterWeight = next == null ? oldMasterWeight : next.masterWeight();
        boolean weightChanged = Math.abs(nextMasterWeight - oldMasterWeight) > 1.0E-9D;
        String previousStatus = state.getStatus();
        state.setTargetMasterWeight(targetMasterWeight);
        state.setTargetRankingLstmWeight(1D - targetMasterWeight);
        state.setMasterWeight(nextMasterWeight);
        state.setRankingLstmWeight(1D - nextMasterWeight);
        state.setCurrentCombinationQualityScore(quality(next));
        state.setBestCombinationQualityScore(best.qualityScore());
        state.setStatus(STATUS_ACTIVE);
        state.setDecisionReason(decisionReason(weightChanged, improvement, oldMasterWeight,
                nextMasterWeight, targetMasterWeight));
        if (weightChanged) {
            state.setWeightVersion(state.getWeightVersion() + 1L);
            state.setEffectiveTradeDate(coveredDates.stream().max(LocalDate::compareTo).orElse(null));
        }
        stateRepository.save(state);
        markMasterActive(masterModelId);
        if (weightChanged) {
            auditService.record(MODEL_NAME, "TEMPORAL_WEIGHT_UPDATED", "automatic-ensemble",
                    state.getRankingLstmModelId(), masterModelId, true, state.getDecisionReason());
        }
        if (STATUS_DEGRADED.equals(previousStatus) || STATUS_UNAVAILABLE.equals(previousStatus)) {
            auditService.record(MODEL_NAME, "TEMPORAL_ENSEMBLE_RECOVERED", "automatic-ensemble",
                    state.getRankingLstmModelId(), masterModelId, true, state.getDecisionReason());
        }
    }

    /**
     * 获取当前融合状态；旧结构状态会直接重建为新版观察状态。
     *
     * @return 当前融合状态
     */
    public synchronized TemporalModelEnsembleStateDocument currentState() {
        TemporalModelEnsembleStateDocument state = stateRepository.findById(ENSEMBLE_STATE_ID).orElse(null);
        if (state == null || !SCHEMA_VERSION.equals(state.getSchemaVersion())) {
            return stateRepository.save(newState());
        }
        return state;
    }

    private Map<String, TemporalModelShadowPredictionDocument> loadMasterOutputs(
            String batchId, String masterModelId) {
        if (masterModelId == null || masterModelId.isBlank()) {
            return Map.of();
        }
        return shadowRepository.findByBaselineBatchId(batchId).stream()
                .filter(item -> masterModelId.equals(item.getModelId()))
                .collect(Collectors.toMap(TemporalModelShadowPredictionDocument::getStockCode,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private TemporalModelOutputCalculator.ModelOutput toLstmOutput(PredictionCandidateItemDto item) {
        if (item == null || item.getCrossSectionPercentile() == null
                || item.getExpectedExcessReturn() == null
                || item.getCalibratedUpProbability() == null
                || item.getDownsideQuantile() == null) {
            return null;
        }
        return new TemporalModelOutputCalculator.ModelOutput(item.getCrossSectionPercentile(),
                item.getExpectedExcessReturn(), item.getCalibratedUpProbability(), item.getDownsideQuantile());
    }

    private TemporalModelOutputCalculator.ModelOutput toMasterOutput(
            TemporalModelShadowPredictionDocument item) {
        if (item == null || item.getRankPercentile() == null
                || item.getExpectedExcessReturn() == null || item.getUpProbability() == null
                || item.getDownsideQuantile() == null) {
            return null;
        }
        return new TemporalModelOutputCalculator.ModelOutput(item.getRankPercentile(),
                item.getExpectedExcessReturn(), item.getUpProbability(), item.getDownsideQuantile());
    }

    private EvaluationSample toEvaluationSample(
            TemporalModelShadowPredictionDocument shadow, ModelPredictionDocument baseline) {
        if (baseline == null || baseline.getActualReturn() == null
                || !Double.isFinite(baseline.getActualReturn())) {
            return null;
        }
        TemporalModelOutputCalculator.ModelOutput lstm = new TemporalModelOutputCalculator.ModelOutput(
                value(baseline.getCrossSectionPercentile()), value(baseline.getExpectedExcessReturn()),
                value(baseline.getCalibratedUpProbability()), value(baseline.getDownsideQuantile()));
        TemporalModelOutputCalculator.ModelOutput master = toMasterOutput(shadow);
        if (!outputCalculator.isValid(lstm) || !outputCalculator.isValid(master)) {
            return null;
        }
        return new EvaluationSample(shadow.getTargetTradeDate(), shadow.getStockCode(),
                lstm, master, baseline.getActualReturn());
    }

    private String resolveRankingLstmModelId(
            TemporalModelEnsembleStateDocument state,
            List<TemporalModelShadowPredictionDocument> shadows,
            List<ModelPredictionDocument> baselines) {
        if (state.getRankingLstmModelId() != null && baselines.stream()
                .anyMatch(item -> state.getRankingLstmModelId().equals(item.getModelId()))) {
            return state.getRankingLstmModelId();
        }
        Map<String, ModelPredictionDocument> byKey = baselines.stream().collect(Collectors.toMap(
                item -> key(item.getBatchId(), item.getStockCode()), Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
        return shadows.stream().sorted(Comparator.comparing(
                        TemporalModelShadowPredictionDocument::getTargetTradeDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(item -> byKey.get(key(item.getBaselineBatchId(), item.getStockCode())))
                .filter(Objects::nonNull)
                .map(ModelPredictionDocument::getModelId)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private double coverageRate(
            LocalDate date,
            List<TemporalModelShadowPredictionDocument> shadows,
            Map<String, Long> baselineCounts,
            long validSampleCount) {
        List<TemporalModelShadowPredictionDocument> daily = shadows.stream()
                .filter(item -> date.equals(item.getTargetTradeDate())).toList();
        long expected = daily.stream().map(TemporalModelShadowPredictionDocument::getBaselineBatchId)
                .filter(Objects::nonNull).distinct()
                .mapToLong(batchId -> baselineCounts.getOrDefault(batchId, 0L)).sum();
        return expected == 0L ? 0D : (double) validSampleCount / expected;
    }

    private void updateSingleModelMetrics(
            TemporalModelEnsembleStateDocument state,
            CombinationMetrics lstm, CombinationMetrics master) {
        state.setRankingLstmRankIc(metric(lstm, CombinationMetrics::rankIc));
        state.setMasterRankIc(metric(master, CombinationMetrics::rankIc));
        state.setRankingLstmIcIr(metric(lstm, CombinationMetrics::icIr));
        state.setMasterIcIr(metric(master, CombinationMetrics::icIr));
        state.setRankingLstmNetReturn(metric(lstm, CombinationMetrics::netReturn));
        state.setMasterNetReturn(metric(master, CombinationMetrics::netReturn));
        state.setRankingLstmMaxDrawdown(metric(lstm, CombinationMetrics::maximumDrawdown));
        state.setMasterMaxDrawdown(metric(master, CombinationMetrics::maximumDrawdown));
        state.setRankingLstmOutputStd(metric(lstm, CombinationMetrics::outputStd));
        state.setMasterOutputStd(metric(master, CombinationMetrics::outputStd));
        state.setRankingLstmQualityScore(quality(lstm));
        state.setMasterQualityScore(quality(master));
    }

    private Double metric(CombinationMetrics metrics, java.util.function.ToDoubleFunction<CombinationMetrics> getter) {
        return metrics == null ? null : getter.applyAsDouble(metrics);
    }

    private Double quality(CombinationMetrics metrics) {
        return metrics == null || !metrics.eligible() ? null : metrics.qualityScore();
    }

    private void observe(
            TemporalModelEnsembleStateDocument state, String reason,
            int evaluatedTradingDays, int evaluatedSamples) {
        state.setStatus(STATUS_OBSERVING);
        state.setDecisionReason(reason);
        state.setEvaluatedTradingDays(evaluatedTradingDays);
        state.setEvaluatedSamples(evaluatedSamples);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
    }

    private void degradeWithoutChangingWeight(TemporalModelEnsembleStateDocument state, String reason) {
        String previousStatus = state.getStatus();
        state.setStatus(STATUS_DEGRADED);
        state.setCurrentCombinationQualityScore(null);
        state.setBestCombinationQualityScore(null);
        state.setTargetRankingLstmWeight(state.getRankingLstmWeight());
        state.setTargetMasterWeight(state.getMasterWeight());
        state.setDecisionReason(reason);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
        if (!STATUS_DEGRADED.equals(previousStatus)) {
            auditService.record(MODEL_NAME, "TEMPORAL_ENSEMBLE_DEGRADED", "automatic-ensemble",
                    state.getRankingLstmModelId(), state.getMasterModelId(), false, reason);
        }
    }

    private void degradeForUnsafeWeightStep(
            TemporalModelEnsembleStateDocument state,
            CombinationMetrics best,
            Double currentScore) {
        String previousStatus = state.getStatus();
        state.setStatus(STATUS_DEGRADED);
        state.setCurrentCombinationQualityScore(currentScore);
        state.setBestCombinationQualityScore(best.qualityScore());
        state.setTargetRankingLstmWeight(best.rankingLstmWeight());
        state.setTargetMasterWeight(best.masterWeight());
        String reason = String.format(
                "目标 MASTER 权重 %.2f，但单次变化 %.2f 范围内没有通过硬门禁的中间组合，保持当前权重 %.2f",
                best.masterWeight(), config.getMaximumWeightChange(), state.getMasterWeight());
        state.setDecisionReason(reason);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
        if (!STATUS_DEGRADED.equals(previousStatus)) {
            auditService.record(MODEL_NAME, "TEMPORAL_ENSEMBLE_DEGRADED", "automatic-ensemble",
                    state.getRankingLstmModelId(), state.getMasterModelId(), false, reason);
        }
    }

    private void recordRuntimeDegradation(TemporalModelEnsembleStateDocument state, String reason) {
        if (STATUS_DEGRADED.equals(state.getStatus()) && reason.equals(state.getDecisionReason())) {
            return;
        }
        state.setStatus(STATUS_DEGRADED);
        state.setDecisionReason(reason);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
        auditService.record(MODEL_NAME, "TEMPORAL_RUNTIME_DEGRADED", "automatic-ensemble",
                state.getRankingLstmModelId(), state.getMasterModelId(), false, reason);
    }

    private IllegalStateException unavailable(TemporalModelEnsembleStateDocument state, String reason) {
        if (!STATUS_UNAVAILABLE.equals(state.getStatus())) {
            state.setStatus(STATUS_UNAVAILABLE);
            state.setDecisionReason(reason);
            state.setUpdatedAt(LocalDateTime.now());
            stateRepository.save(state);
            auditService.record(MODEL_NAME, "TEMPORAL_ENSEMBLE_UNAVAILABLE", "automatic-ensemble",
                    state.getRankingLstmModelId(), state.getMasterModelId(), false, reason);
        }
        return new IllegalStateException(reason);
    }

    private void resetState(
            TemporalModelEnsembleStateDocument state,
            String rankingLstmModelId, String masterModelId, String reason) {
        double previousMasterWeight = state.getMasterWeight();
        state.setSchemaVersion(SCHEMA_VERSION);
        state.setRankingLstmModelId(rankingLstmModelId);
        state.setMasterModelId(masterModelId);
        state.setRankingLstmWeight(1D);
        state.setMasterWeight(0D);
        state.setTargetRankingLstmWeight(1D);
        state.setTargetMasterWeight(0D);
        state.setCurrentCombinationQualityScore(null);
        state.setBestCombinationQualityScore(null);
        state.setStatus(STATUS_OBSERVING);
        state.setWeightVersion(state.getWeightVersion() + 1L);
        state.setEffectiveTradeDate(null);
        clearMetrics(state);
        state.setDecisionReason(reason);
        state.setEvaluatedTradingDays(0);
        state.setEvaluatedSamples(0);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
        if (Math.abs(previousMasterWeight) > 1.0E-9D) {
            auditService.record(MODEL_NAME, "TEMPORAL_WEIGHT_RESET", "automatic-ensemble",
                    rankingLstmModelId, masterModelId, true, reason);
        }
    }

    private TemporalModelEnsembleStateDocument newState() {
        TemporalModelEnsembleStateDocument state = new TemporalModelEnsembleStateDocument();
        state.setId(ENSEMBLE_STATE_ID);
        state.setSchemaVersion(SCHEMA_VERSION);
        state.setRankingLstmWeight(1D);
        state.setMasterWeight(0D);
        state.setTargetRankingLstmWeight(1D);
        state.setTargetMasterWeight(0D);
        state.setStatus(STATUS_OBSERVING);
        state.setWeightVersion(1L);
        state.setDecisionReason("等待 Ranking LSTM 与 MASTER 同版本样本外评估");
        state.setUpdatedAt(LocalDateTime.now());
        return state;
    }

    private void clearMetrics(TemporalModelEnsembleStateDocument state) {
        state.setRankingLstmRankIc(null);
        state.setMasterRankIc(null);
        state.setRankingLstmIcIr(null);
        state.setMasterIcIr(null);
        state.setRankingLstmNetReturn(null);
        state.setMasterNetReturn(null);
        state.setRankingLstmMaxDrawdown(null);
        state.setMasterMaxDrawdown(null);
        state.setRankingLstmOutputStd(null);
        state.setMasterOutputStd(null);
        state.setRankingLstmQualityScore(null);
        state.setMasterQualityScore(null);
    }

    private void markMasterActive(String modelId) {
        modelRepository.findById(modelId).ifPresent(model -> {
            if (!"ACTIVE".equals(model.getStatus())) {
                model.setStatus("ACTIVE");
                modelRepository.save(model);
            }
        });
    }

    private void retireMaster(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return;
        }
        modelRepository.findById(modelId).ifPresent(model -> {
            model.setStatus("RETIRED");
            modelRepository.save(model);
        });
    }

    private boolean newsFeaturesEmbedded(String lstmModelId, String masterModelId, double masterWeight) {
        if (!config.isNewsFeaturesEmbedded() || config.getNewsFeatureVersion() == null
                || config.getNewsFeatureVersion().isBlank()) {
            return false;
        }
        String lstmFeatureVersion = modelRepository.findById(lstmModelId)
                .map(LstmModelDocument::getFeatureVersion).orElse(null);
        if (lstmFeatureVersion == null || lstmFeatureVersion.isBlank()) {
            return false;
        }
        if (masterWeight == 0D) {
            return true;
        }
        String masterFeatureVersion = modelRepository.findById(masterModelId)
                .map(LstmModelDocument::getFeatureVersion).orElse(null);
        return Objects.equals(lstmFeatureVersion, masterFeatureVersion);
    }

    private String decisionReason(
            boolean weightChanged, double improvement, double oldMasterWeight,
            double newMasterWeight, double targetMasterWeight) {
        if (!Double.isFinite(improvement)
                || improvement >= config.getMinimumCombinationImprovement()) {
            if (weightChanged) {
                return String.format("最优 MASTER 目标权重 %.2f，当前权重由 %.2f 调整为 %.2f",
                        targetMasterWeight, oldMasterWeight, newMasterWeight);
            }
            return String.format("当前权重已达到最优 MASTER 权重 %.2f", targetMasterWeight);
        }
        return String.format("最优组合质量提升 %.4f 未达到调权阈值 %.4f，保持当前权重",
                improvement, config.getMinimumCombinationImprovement());
    }

    private Double estimateNetReturn(double expectedExcessReturn) {
        BigDecimal slippageRate = costProperties.getSlippageBps().multiply(BigDecimal.valueOf(2D))
                .divide(BigDecimal.valueOf(10000D), 10, RoundingMode.HALF_UP);
        BigDecimal cost = costProperties.getCommissionRate().multiply(BigDecimal.valueOf(2D))
                .add(costProperties.getTransferFeeRate().multiply(BigDecimal.valueOf(2D)))
                .add(costProperties.getStampDutyRate())
                .add(slippageRate)
                .add(costProperties.getCandidateSafetyMarginRate());
        return BigDecimal.valueOf(expectedExcessReturn).subtract(cost).doubleValue();
    }

    private double value(Double value) {
        return value == null ? Double.NaN : value;
    }

    private String key(String batchId, String stockCode) {
        return batchId + "|" + stockCode;
    }
}
// AI_GENERATE_END --------------------
