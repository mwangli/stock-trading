// AI_GENERATE_START -----------
package com.stock.model.service;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ranking LSTM 与 MASTER 的滚动评估、动态权重和候选融合服务。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemporalModelEnsembleService {

    /** 全局时序模型融合状态主键。 */
    public static final String ENSEMBLE_STATE_ID = "GLOBAL_TEMPORAL_ENSEMBLE";

    private final TemporalEnsembleConfig config;
    private final PredictionCostProperties costProperties;
    private final TemporalModelShadowPredictionRepository shadowRepository;
    private final TemporalModelEnsembleStateRepository stateRepository;
    private final ModelPredictionRepository predictionRepository;
    private final LstmModelRepository modelRepository;
    private final ModelPromotionPolicy promotionPolicy;

    /**
     * 注册新的 MASTER 候选并重置影子观察状态。
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
        resetState(state, "MASTER 候选已注册，等待 10 个交易日和 500 个影子样本");
    }

    /**
     * 判断自动训练链是否需要生成新的 MASTER 挑战者。
     *
     * @param baselineModelId 新晋级的基础时序模型文档 ID
     * @return 当前没有正在观察或已激活的 MASTER 时返回 true
     */
    public synchronized boolean shouldTrainMasterCandidate(String baselineModelId) {
        TemporalModelEnsembleStateDocument state = currentState();
        if (state.getMasterModelId() == null || state.getMasterModelId().isBlank()) {
            return true;
        }
        LstmModelDocument baseline = modelRepository.findById(baselineModelId).orElse(null);
        LstmModelDocument master = modelRepository.findById(state.getMasterModelId()).orElse(null);
        if (baseline != null && master != null && Objects.equals(
                baseline.getFeatureVersion(), master.getFeatureVersion())) {
            return false;
        }
        retireMaster(state.getMasterModelId());
        state.setMasterModelId(null);
        resetState(state, "基础模型特征契约已变化，旧 MASTER 已退役并等待自动重训");
        return true;
    }

    /**
     * 在生产开关、滚动门槛、版本和覆盖率全部满足时写入融合 Rank Score。
     *
     * @param baseline Ranking LSTM 生产候选批次
     * @return 原对象或已写入融合字段的候选批次
     */
    public PredictionCandidateBatchDto apply(PredictionCandidateBatchDto baseline) {
        if (baseline == null || !config.isProductionBlendEnabled()) {
            return baseline;
        }
        TemporalModelEnsembleStateDocument state = currentState();
        String masterModelId = state.getMasterModelId();
        if (!state.isProductionEnabled() || masterModelId == null || masterModelId.isBlank()) {
            return baseline;
        }
        List<TemporalModelShadowPredictionDocument> shadows = shadowRepository
                .findByBaselineBatchId(baseline.getBatchId());
        Map<String, TemporalModelShadowPredictionDocument> byStock = shadows.stream()
                .filter(item -> masterModelId.equals(item.getModelId()))
                .collect(Collectors.toMap(
                        TemporalModelShadowPredictionDocument::getStockCode,
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        int expected = baseline.getItems() == null ? 0 : baseline.getItems().size();
        double coverageRate = expected == 0 ? 0D : (double) byStock.size() / expected;
        if (expected == 0 || coverageRate < 0.95D) {
            log.warn("MASTER 影子结果覆盖不完整，保持 Ranking LSTM: batchId={}, expected={}, actual={}",
                    baseline.getBatchId(), expected, byStock.size());
            return baseline;
        }
        String sharedFeatureVersion;
        try {
            sharedFeatureVersion = requireSameFeatureVersion(
                    baseline.getModelId(), masterModelId);
        } catch (IllegalStateException exception) {
            log.warn("时序模型版本契约不满足融合条件，保持 Ranking LSTM: batchId={}, reason={}",
                    baseline.getBatchId(), exception.getMessage());
            return baseline;
        }
        baseline.setNewsFeaturesEmbedded(config.isNewsFeaturesEmbedded()
                && config.getNewsFeatureVersion() != null
                && config.getNewsFeatureVersion().equals(sharedFeatureVersion));
        double[] weights = normalizedWeights(state.getRankingLstmWeight(), state.getMasterWeight());
        for (PredictionCandidateItemDto item : baseline.getItems()) {
            Double lstmRank = item.getCrossSectionPercentile() != null
                    ? item.getCrossSectionPercentile() : item.getPredictedReturnPercentile();
            TemporalModelShadowPredictionDocument shadow = byStock.get(item.getStockCode());
            Double masterRank = shadow == null ? null : shadow.getRankPercentile();
            if (!finiteRank(lstmRank) || (shadow != null && !finiteRank(masterRank))) {
                log.warn("时序模型融合分数无效，保持 Ranking LSTM: batchId={}, stockCode={}",
                        baseline.getBatchId(), item.getStockCode());
                return baseline;
            }
        }
        for (PredictionCandidateItemDto item : baseline.getItems()) {
            Double lstmRank = item.getCrossSectionPercentile() != null
                    ? item.getCrossSectionPercentile() : item.getPredictedReturnPercentile();
            TemporalModelShadowPredictionDocument shadow = byStock.get(item.getStockCode());
            if (shadow == null) {
                item.setEnsembleRankScore(lstmRank);
                item.setRankingLstmWeight(1D);
                item.setMasterWeight(0D);
                continue;
            }
            Double masterRank = shadow.getRankPercentile();
            item.setEnsembleRankScore(weights[0] * lstmRank + weights[1] * masterRank);
            item.setRankingLstmWeight(weights[0]);
            item.setMasterWeight(weights[1]);
            item.setMasterModelId(masterModelId);
        }
        return baseline;
    }

    /**
     * 根据最近交易日的样本外结果刷新两个模型的滚动指标和生产权重。
     */
    public void refreshState() {
        TemporalModelEnsembleStateDocument state = currentState();
        String masterModelId = state.getMasterModelId();
        if (masterModelId == null || masterModelId.isBlank()) {
            return;
        }
        int pageSize = Math.max(1000, config.getRollingWindowTradingDays() * 6000);
        List<TemporalModelShadowPredictionDocument> all = shadowRepository
                .findByModelIdAndEvaluationStatusOrderByTargetTradeDateDesc(
                        masterModelId, "EVALUATED", PageRequest.of(0, pageSize));
        List<LocalDate> dates = all.stream()
                .map(TemporalModelShadowPredictionDocument::getTargetTradeDate)
                .filter(Objects::nonNull)
                .distinct()
                .limit(Math.max(1, config.getRollingWindowTradingDays()))
                .toList();
        if (dates.size() < config.getMinimumEvaluatedTradingDays()) {
            holdCandidate(state, "MASTER 影子评估交易日不足", dates.size(), all.size());
            return;
        }
        List<TemporalModelShadowPredictionDocument> shadows = all.stream()
                .filter(item -> dates.contains(item.getTargetTradeDate()))
                .toList();
        List<String> batchIds = shadows.stream()
                .map(TemporalModelShadowPredictionDocument::getBaselineBatchId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, ModelPredictionDocument> baselines = predictionRepository.findByBatchIdIn(batchIds).stream()
                .filter(item -> item.getActualReturn() != null && Double.isFinite(item.getActualReturn()))
                .collect(Collectors.toMap(
                        item -> key(item.getBatchId(), item.getStockCode()), Function.identity(),
                        (left, right) -> left, LinkedHashMap::new));
        List<EvaluationPoint> points = shadows.stream()
                .map(item -> toPoint(item, baselines.get(key(item.getBaselineBatchId(), item.getStockCode()))))
                .filter(Objects::nonNull)
                .toList();
        if (points.size() < config.getMinimumEvaluatedSamples()) {
            holdCandidate(state, "MASTER 影子评估样本不足", dates.size(), points.size());
            return;
        }
        ModelMetrics lstmMetrics = metrics(
                points, EvaluationPoint::lstmRank, EvaluationPoint::lstmRawScore);
        ModelMetrics masterMetrics = metrics(
                points, EvaluationPoint::masterRank, EvaluationPoint::masterRawScore);
        boolean lstmEligible = passesHardGate(lstmMetrics);
        boolean masterEligible = passesHardGate(masterMetrics);
        double lstmQuality = promotionPolicy.qualityScore(
                lstmMetrics.rankIc(), lstmMetrics.netReturn(), lstmMetrics.maximumDrawdown());
        double masterQuality = promotionPolicy.qualityScore(
                masterMetrics.rankIc(), masterMetrics.netReturn(), masterMetrics.maximumDrawdown());
        double scoreAdvantage = masterQuality - lstmQuality;
        boolean qualityImproved = scoreAdvantage >= promotionPolicy.minimumQualityScoreAdvantage();
        boolean productionEnabled = config.isProductionBlendEnabled()
                && lstmEligible && masterEligible && qualityImproved;
        double masterWeight = productionEnabled ? promotionPolicy.masterWeight(scoreAdvantage) : 0D;
        double[] weights = new double[]{1D - masterWeight, masterWeight};

        state.setRankingLstmWeight(weights[0]);
        state.setMasterWeight(weights[1]);
        state.setProductionEnabled(productionEnabled);
        state.setRankingLstmRankIc(lstmMetrics.rankIc());
        state.setMasterRankIc(masterMetrics.rankIc());
        state.setRankingLstmIcIr(lstmMetrics.icIr());
        state.setMasterIcIr(masterMetrics.icIr());
        state.setRankingLstmNetReturn(lstmMetrics.netReturn());
        state.setMasterNetReturn(masterMetrics.netReturn());
        state.setRankingLstmMaxDrawdown(lstmMetrics.maximumDrawdown());
        state.setMasterMaxDrawdown(masterMetrics.maximumDrawdown());
        state.setRankingLstmOutputStd(lstmMetrics.outputStd());
        state.setMasterOutputStd(masterMetrics.outputStd());
        state.setRankingLstmQualityScore(lstmQuality);
        state.setMasterQualityScore(masterQuality);
        state.setDecisionReason(decisionReason(lstmEligible, masterEligible, qualityImproved));
        state.setEvaluatedTradingDays(dates.size());
        state.setEvaluatedSamples(points.size());
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
        if (productionEnabled) {
            markMasterActive(masterModelId);
        } else if (!masterEligible || ("ACTIVE".equals(modelStatus(masterModelId)) && !qualityImproved)) {
            retireMaster(masterModelId);
            state.setMasterModelId(null);
            state.setDecisionReason("MASTER 硬门禁失败或激活后失去质量优势，已自动回退并退役");
            stateRepository.save(state);
        }
    }

    /**
     * 获取全局融合状态；首次调用创建仅使用 Ranking LSTM 的观察状态。
     *
     * @return 当前融合状态
     */
    public TemporalModelEnsembleStateDocument currentState() {
        return stateRepository.findById(ENSEMBLE_STATE_ID).orElseGet(() -> {
            TemporalModelEnsembleStateDocument state = new TemporalModelEnsembleStateDocument();
            state.setId(ENSEMBLE_STATE_ID);
            state.setRankingLstmWeight(1D);
            state.setMasterWeight(0D);
            state.setProductionEnabled(false);
            state.setDecisionReason("等待 MASTER 影子样本和滚动能力指标");
            state.setEvaluatedTradingDays(0);
            state.setEvaluatedSamples(0);
            state.setUpdatedAt(LocalDateTime.now());
            return stateRepository.save(state);
        });
    }

    private EvaluationPoint toPoint(
            TemporalModelShadowPredictionDocument shadow, ModelPredictionDocument baseline) {
        if (baseline == null || !finiteRank(shadow.getRankPercentile())) {
            return null;
        }
        Double lstmRank = baseline.getCrossSectionPercentile() != null
                ? baseline.getCrossSectionPercentile() : baseline.getPredictedReturnPercentile();
        if (!finiteRank(lstmRank)) {
            return null;
        }
        return new EvaluationPoint(
                shadow.getTargetTradeDate(), lstmRank, shadow.getRankPercentile(),
                baseline.getRankScore() == null ? baseline.getPredictedReturn() : baseline.getRankScore(),
                shadow.getRankScore(), baseline.getActualReturn());
    }

    private ModelMetrics metrics(
            List<EvaluationPoint> points,
            Function<EvaluationPoint, Double> scoreSelector,
            Function<EvaluationPoint, Double> rawScoreSelector) {
        Map<LocalDate, List<EvaluationPoint>> byDate = points.stream()
                .collect(Collectors.groupingBy(EvaluationPoint::date, LinkedHashMap::new, Collectors.toList()));
        List<Double> dailyIc = new ArrayList<>();
        List<Double> dailyReturns = new ArrayList<>();
        List<Double> rawScores = new ArrayList<>();
        byDate.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<EvaluationPoint> daily = entry.getValue();
            if (daily.size() >= 3) {
                dailyIc.add(rankCorrelation(daily, scoreSelector));
                dailyReturns.add(topDecileReturn(daily, scoreSelector));
            }
            daily.stream().map(rawScoreSelector)
                    .filter(Objects::nonNull)
                    .filter(Double::isFinite)
                    .forEach(rawScores::add);
        });
        double rankIc = mean(dailyIc);
        double icStd = standardDeviation(dailyIc);
        return new ModelMetrics(rankIc, icStd <= 1.0E-12D ? 0D : rankIc / icStd,
                mean(dailyReturns), maximumDrawdown(dailyReturns), standardDeviation(rawScores));
    }

    private double rankCorrelation(
            List<EvaluationPoint> points, Function<EvaluationPoint, Double> scoreSelector) {
        List<Double> scores = points.stream().map(scoreSelector).toList();
        List<Double> returns = points.stream().map(EvaluationPoint::actualReturn).toList();
        return pearson(ranks(scores), ranks(returns));
    }

    private List<Double> ranks(List<Double> values) {
        List<Integer> indexes = java.util.stream.IntStream.range(0, values.size()).boxed()
                .sorted(Comparator.comparingDouble(values::get)).toList();
        double[] ranks = new double[values.size()];
        int denominator = Math.max(1, values.size() - 1);
        int position = 0;
        while (position < indexes.size()) {
            int end = position + 1;
            double value = values.get(indexes.get(position));
            while (end < indexes.size()
                    && Double.compare(values.get(indexes.get(end)), value) == 0) {
                end++;
            }
            double rank = ((position + end - 1D) / 2D) / denominator;
            for (int cursor = position; cursor < end; cursor++) {
                ranks[indexes.get(cursor)] = rank;
            }
            position = end;
        }
        return java.util.Arrays.stream(ranks).boxed().toList();
    }

    private double pearson(List<Double> left, List<Double> right) {
        double leftMean = mean(left);
        double rightMean = mean(right);
        double covariance = 0D;
        double leftVariance = 0D;
        double rightVariance = 0D;
        for (int index = 0; index < left.size(); index++) {
            double leftDelta = left.get(index) - leftMean;
            double rightDelta = right.get(index) - rightMean;
            covariance += leftDelta * rightDelta;
            leftVariance += leftDelta * leftDelta;
            rightVariance += rightDelta * rightDelta;
        }
        double denominator = Math.sqrt(leftVariance * rightVariance);
        return denominator <= 1.0E-12D ? 0D : covariance / denominator;
    }

    private double topDecileReturn(
            List<EvaluationPoint> points, Function<EvaluationPoint, Double> scoreSelector) {
        int count = Math.max(1, (int) Math.ceil(points.size() * 0.1D));
        double gross = points.stream()
                .sorted(Comparator.comparingDouble(
                        (EvaluationPoint point) -> scoreSelector.apply(point)).reversed())
                .limit(count)
                .mapToDouble(EvaluationPoint::actualReturn)
                .average()
                .orElse(0D);
        return gross - roundTripCost();
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

    private double maximumDrawdown(List<Double> dailyReturns) {
        double equity = 1D;
        double peak = 1D;
        double maximumDrawdown = 0D;
        for (Double dailyReturn : dailyReturns) {
            equity *= 1D + dailyReturn;
            peak = Math.max(peak, equity);
            maximumDrawdown = Math.max(maximumDrawdown, peak <= 0D ? 0D : (peak - equity) / peak);
        }
        return maximumDrawdown;
    }

    private boolean passesHardGate(ModelMetrics metrics) {
        return Double.isFinite(metrics.rankIc())
                && Double.isFinite(metrics.netReturn())
                && Double.isFinite(metrics.maximumDrawdown())
                && Double.isFinite(metrics.outputStd())
                && promotionPolicy.passesPerformanceHardGate(
                        metrics.netReturn(), metrics.maximumDrawdown())
                && metrics.outputStd() >= config.getMinimumOutputStd();
    }

    private double[] normalizedWeights(double lstmWeight, double masterWeight) {
        double safeLstm = Double.isFinite(lstmWeight) ? Math.max(0D, lstmWeight) : 0D;
        double safeMaster = Double.isFinite(masterWeight) ? Math.max(0D, masterWeight) : 0D;
        double total = safeLstm + safeMaster;
        if (total <= 1.0E-12D) {
            return new double[]{1D, 0D};
        }
        return new double[]{safeLstm / total, safeMaster / total};
    }

    private void holdCandidate(
            TemporalModelEnsembleStateDocument state, String reason,
            int evaluatedTradingDays, int evaluatedSamples) {
        state.setRankingLstmWeight(1D);
        state.setMasterWeight(0D);
        state.setProductionEnabled(false);
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
        state.setDecisionReason(reason);
        state.setEvaluatedTradingDays(evaluatedTradingDays);
        state.setEvaluatedSamples(evaluatedSamples);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
    }

    private void resetState(TemporalModelEnsembleStateDocument state, String reason) {
        holdCandidate(state, reason, 0, 0);
    }

    private void markMasterActive(String modelId) {
        modelRepository.findById(modelId).ifPresent(model -> {
            model.setStatus("ACTIVE");
            modelRepository.save(model);
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

    private String modelStatus(String modelId) {
        return modelRepository.findById(modelId).map(LstmModelDocument::getStatus).orElse(null);
    }

    private String requireSameFeatureVersion(String baselineModelId, String masterModelId) {
        LstmModelDocument baseline = modelRepository.findById(baselineModelId)
                .orElseThrow(() -> new IllegalStateException("Ranking LSTM 基线模型不存在"));
        LstmModelDocument master = modelRepository.findById(masterModelId)
                .orElseThrow(() -> new IllegalStateException("MASTER 候选模型不存在"));
        if (baseline.getFeatureVersion() == null
                || !baseline.getFeatureVersion().equals(master.getFeatureVersion())) {
            throw new IllegalStateException("时序模型特征契约版本不一致，禁止生产融合");
        }
        return baseline.getFeatureVersion();
    }

    private String decisionReason(
            boolean lstmEligible, boolean masterEligible, boolean qualityImproved) {
        if (lstmEligible && masterEligible && qualityImproved) {
            return "MASTER 通过硬门禁且综合质量分达到自动晋级优势，已启用融合";
        }
        if (lstmEligible && masterEligible) {
            return "MASTER 通过硬门禁但综合质量优势不足，继续保持影子观察";
        }
        if (lstmEligible) {
            return "MASTER 未通过硬门禁，权重已降为 0";
        }
        if (masterEligible) {
            return "Ranking LSTM 未通过硬门禁，生产融合保持关闭";
        }
        return "两个时序模型均未通过硬门禁，生产融合关闭";
    }

    private boolean finiteRank(Double value) {
        return value != null && Double.isFinite(value) && value >= 0D && value <= 1D;
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
    }

    private double standardDeviation(List<Double> values) {
        if (values.size() < 2) {
            return 0D;
        }
        double mean = mean(values);
        double variance = values.stream()
                .mapToDouble(value -> (value - mean) * (value - mean))
                .sum() / (values.size() - 1D);
        return Math.sqrt(Math.max(0D, variance));
    }

    private String key(String batchId, String stockCode) {
        return batchId + "|" + stockCode;
    }

    private record EvaluationPoint(
            LocalDate date, double lstmRank, double masterRank,
            Double lstmRawScore, Double masterRawScore, double actualReturn) {
    }

    private record ModelMetrics(
            double rankIc, double icIr, double netReturn,
            double maximumDrawdown, double outputStd) {
    }
}
// AI_GENERATE_END -----------
