// AI_GENERATE_START- --------
package com.stock.model.algorithm;

import com.stock.model.config.PredictionCostProperties;
import com.stock.model.config.TemporalEnsembleConfig;
import com.stock.model.service.ModelPromotionPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 双模型连续权重回放和最优组合选择器。
 *
 * @see <a href="http://localhost:5173/docs/topics/modelLifecycle/temporal-model-ensemble">
 *     双模型连续权重融合详细设计</a>
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class TemporalModelWeightOptimizer {

    private static final double EPSILON = 1.0E-12D;
    private static final double QUALITY_TIE_EPSILON = 1.0E-9D;

    private final TemporalModelOutputCalculator outputCalculator;
    private final TemporalEnsembleConfig config;
    private final PredictionCostProperties costProperties;
    private final ModelPromotionPolicy promotionPolicy;

    /**
     * 回放全部候选权重并按质量分、当前权重距离和较低 MASTER 权重确定唯一最优组合。
     *
     * @param samples 最近样本外窗口的双模型输出与实际收益
     * @param currentMasterWeight 当前 MASTER 权重
     * @return 当前组合、最优合格组合和全部合格组合
     */
    public OptimizationResult optimize(List<EvaluationSample> samples, double currentMasterWeight) {
        if (!probability(currentMasterWeight)) {
            throw new IllegalArgumentException("当前 MASTER 权重必须位于 0 到 1");
        }
        if (samples == null || samples.isEmpty()) {
            return new OptimizationResult(evaluate(samples, currentMasterWeight), null, List.of());
        }
        double step = config.getWeightSearchStep();
        if (!Double.isFinite(step) || step <= 0D || step > 1D) {
            throw new IllegalStateException("权重搜索步长必须位于 0 到 1: " + step);
        }
        int steps = (int) Math.round(1D / step);
        if (steps <= 0 || Math.abs(steps * step - 1D) > 1.0E-9D) {
            throw new IllegalStateException("权重搜索步长必须能够从 0 完整覆盖到 1: " + step);
        }
        List<CombinationMetrics> eligible = new ArrayList<>();
        for (int index = 0; index <= steps; index++) {
            double masterWeight = index == steps ? 1D : roundWeight(index * step);
            CombinationMetrics metrics = evaluate(samples, masterWeight);
            if (metrics != null && metrics.eligible()) {
                eligible.add(metrics);
            }
        }
        CombinationMetrics best = selectBest(eligible, currentMasterWeight);
        return new OptimizationResult(evaluate(samples, currentMasterWeight), best, List.copyOf(eligible));
    }

    CombinationMetrics selectBest(
            List<CombinationMetrics> eligibleCombinations,
            double currentMasterWeight) {
        if (eligibleCombinations == null || eligibleCombinations.isEmpty()) {
            return null;
        }
        double maximumQualityScore = eligibleCombinations.stream()
                .filter(CombinationMetrics::eligible)
                .mapToDouble(CombinationMetrics::qualityScore)
                .max().orElse(Double.NEGATIVE_INFINITY);
        return eligibleCombinations.stream()
                .filter(CombinationMetrics::eligible)
                .filter(item -> maximumQualityScore - item.qualityScore() <= QUALITY_TIE_EPSILON)
                .min(Comparator.comparingDouble((CombinationMetrics item) -> Math.abs(
                                item.masterWeight() - currentMasterWeight))
                        .thenComparingDouble(CombinationMetrics::masterWeight))
                .orElse(null);
    }

    /**
     * 计算指定 MASTER 权重的样本外组合指标。
     *
     * @param samples 双模型样本外输出
     * @param masterWeight MASTER 权重
     * @return 组合指标；输入不合法时返回 null
     */
    public CombinationMetrics evaluate(List<EvaluationSample> samples, double masterWeight) {
        if (samples == null || samples.isEmpty() || !probability(masterWeight)
                || samples.stream().anyMatch(sample -> sample == null
                || sample.date() == null || sample.stockCode() == null || sample.stockCode().isBlank()
                || !Double.isFinite(sample.actualReturn()))) {
            return null;
        }
        double lstmWeight = 1D - masterWeight;
        try {
            Map<LocalDate, List<EvaluationSample>> byDate = samples.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.groupingBy(
                            EvaluationSample::date, LinkedHashMap::new, Collectors.toList()));
            List<Double> dailyIc = new ArrayList<>();
            List<Double> dailyReturns = new ArrayList<>();
            List<Double> modelScores = new ArrayList<>();
            for (Map.Entry<LocalDate, List<EvaluationSample>> entry : byDate.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).toList()) {
                List<EvaluationSample> dailySamples = entry.getValue();
                List<TemporalModelOutputCalculator.EffectiveOutput> outputs = outputCalculator.calculateDaily(
                        dailySamples.stream().map(sample -> new TemporalModelOutputCalculator.WeightedOutput(
                                sample.stockCode(), sample.rankingLstm(), sample.master())).toList(),
                        lstmWeight, masterWeight);
                if (outputs.size() < 3) {
                    continue;
                }
                Map<String, Double> actualReturns = dailySamples.stream().collect(Collectors.toMap(
                        EvaluationSample::stockCode, EvaluationSample::actualReturn,
                        (left, right) -> left, LinkedHashMap::new));
                dailyIc.add(rankCorrelation(outputs, actualReturns));
                dailyReturns.add(topDecileReturn(outputs, actualReturns));
                outputs.stream().map(TemporalModelOutputCalculator.EffectiveOutput::modelScore)
                        .forEach(modelScores::add);
            }
            double rankIc = mean(dailyIc);
            double icIr = informationCoefficientRatio(dailyIc);
            double netReturn = mean(dailyReturns);
            double maximumDrawdown = maximumDrawdown(dailyReturns);
            double outputStd = standardDeviation(modelScores);
            boolean eligible = Double.isFinite(rankIc)
                    && Double.isFinite(icIr)
                    && Double.isFinite(netReturn)
                    && Double.isFinite(maximumDrawdown)
                    && Double.isFinite(outputStd)
                    && outputStd >= config.getMinimumOutputStd()
                    && promotionPolicy.passesPerformanceHardGate(netReturn, maximumDrawdown);
            double qualityScore = eligible
                    ? promotionPolicy.qualityScore(rankIc, netReturn, maximumDrawdown)
                    : Double.NEGATIVE_INFINITY;
            return new CombinationMetrics(lstmWeight, masterWeight, rankIc, icIr, netReturn,
                    maximumDrawdown, outputStd, qualityScore, eligible);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 判断最优组合是否达到最小调权改善阈值。
     *
     * @param current 当前组合指标，可为空或不合格
     * @param best 最优合格组合指标
     * @return 当前组合不合格或最优组合提升达到阈值时返回 true
     */
    public boolean shouldAdjust(CombinationMetrics current, CombinationMetrics best) {
        if (best == null || !best.eligible()) {
            return false;
        }
        return current == null || !current.eligible()
                || best.qualityScore() - current.qualityScore()
                >= config.getMinimumCombinationImprovement();
    }


    /**
     * 从已通过全部硬门禁的组合中选择单次允许到达且最接近目标的下一版权重。
     *
     * @param currentMasterWeight 当前 MASTER 权重
     * @param target 目标合格组合
     * @param eligibleCombinations 全部合格组合
     * @return 合格步进组合；没有可安全前进的组合时返回 null
     */
    public CombinationMetrics nextEligibleCombination(
            double currentMasterWeight,
            CombinationMetrics target,
            List<CombinationMetrics> eligibleCombinations) {
        if (!probability(currentMasterWeight) || target == null || !target.eligible()
                || eligibleCombinations == null || eligibleCombinations.isEmpty()) {
            return null;
        }
        double direction = Math.signum(target.masterWeight() - currentMasterWeight);
        if (direction == 0D) {
            return eligibleCombinations.stream()
                    .filter(item -> sameWeight(item.masterWeight(), currentMasterWeight))
                    .findFirst().orElse(target);
        }
        double maximumChange = config.getMaximumWeightChange();
        if (!Double.isFinite(maximumChange) || maximumChange <= 0D || maximumChange > 1D) {
            throw new IllegalStateException("单次最大权重变化必须位于 0 到 1");
        }
        return eligibleCombinations.stream()
                .filter(CombinationMetrics::eligible)
                .filter(item -> direction * (item.masterWeight() - currentMasterWeight) > EPSILON)
                .filter(item -> direction * (target.masterWeight() - item.masterWeight()) >= -EPSILON)
                .filter(item -> Math.abs(item.masterWeight() - currentMasterWeight)
                        <= maximumChange + EPSILON)
                .min(Comparator.comparingDouble((CombinationMetrics item) -> Math.abs(
                                item.masterWeight() - target.masterWeight()))
                        .thenComparingDouble(CombinationMetrics::masterWeight))
                .orElse(null);
    }

    private double rankCorrelation(
            List<TemporalModelOutputCalculator.EffectiveOutput> outputs,
            Map<String, Double> actualReturns) {
        List<Double> scores = outputs.stream()
                .map(TemporalModelOutputCalculator.EffectiveOutput::modelScore).toList();
        List<Double> returns = outputs.stream().map(item -> actualReturns.get(item.stockCode())).toList();
        return pearson(ranks(scores), ranks(returns));
    }

    private double topDecileReturn(
            List<TemporalModelOutputCalculator.EffectiveOutput> outputs,
            Map<String, Double> actualReturns) {
        int count = Math.max(1, (int) Math.ceil(outputs.size() * 0.1D));
        double gross = outputs.stream()
                .sorted(Comparator.comparingDouble(
                        TemporalModelOutputCalculator.EffectiveOutput::modelScore).reversed())
                .limit(count)
                .mapToDouble(item -> actualReturns.get(item.stockCode()))
                .average()
                .orElse(0D);
        return gross - roundTripCost();
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
        return denominator <= EPSILON ? 0D : covariance / denominator;
    }

    private double informationCoefficientRatio(List<Double> dailyIc) {
        double deviation = standardDeviation(dailyIc);
        return deviation <= EPSILON ? 0D : mean(dailyIc) / deviation;
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

    private boolean probability(double value) {
        return Double.isFinite(value) && value >= 0D && value <= 1D;
    }

    private double roundWeight(double value) {
        return Math.round(value * 1000000D) / 1000000D;
    }

    private boolean sameWeight(double left, double right) {
        return Math.abs(left - right) <= EPSILON;
    }

    /** 单个股票日期的双模型样本外评估输入。 */
    public record EvaluationSample(
            LocalDate date,
            String stockCode,
            TemporalModelOutputCalculator.ModelOutput rankingLstm,
            TemporalModelOutputCalculator.ModelOutput master,
            double actualReturn) {
    }

    /** 指定双模型权重的回放指标。 */
    public record CombinationMetrics(
            double rankingLstmWeight,
            double masterWeight,
            double rankIc,
            double icIr,
            double netReturn,
            double maximumDrawdown,
            double outputStd,
            double qualityScore,
            boolean eligible) {
    }

    /** 连续权重搜索结果。 */
    public record OptimizationResult(
            CombinationMetrics current,
            CombinationMetrics best,
            List<CombinationMetrics> eligibleCombinations) {
    }
}
// AI_GENERATE_END- --------
