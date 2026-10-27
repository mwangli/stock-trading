// AI_GENERATE_START-------
package com.stock.model.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 横截面排序模型验证指标计算器。
 * 负责计算 Rank IC、Top-K 成本后收益、选模分数和输出塌缩状态。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class RankingValidationMetricsCalculator {

    /**
     * 计算单个验证集合的排序指标。
     *
     * @param rankScores 模型排序分数
     * @param actualReturns 实际中性收益
     * @param normalizedLoss 当前验证损失
     * @param costThreshold 单次组合成本阈值
     * @return 排序验证指标
     */
    public RankingValidationMetrics calculate(
            double[] rankScores, double[] actualReturns, double normalizedLoss, double costThreshold) {
        return calculate(rankScores, actualReturns, null, null, normalizedLoss, costThreshold, 1D);
    }

    /**
     * 计算包含概率校准质量的排序指标。
     *
     * @param rankScores 模型排序分数
     * @param actualReturns 实际中性收益
     * @param upProbabilities 原始上涨概率
     * @param directionLabels 方向标签，取值为 0、0.5 或 1
     * @param normalizedLoss 当前验证损失
     * @param costThreshold 单次组合成本阈值
     * @param probabilityTemperature 概率温度参数
     * @return 排序与概率验证指标
     */
    public RankingValidationMetrics calculate(
            double[] rankScores,
            double[] actualReturns,
            double[] upProbabilities,
            double[] directionLabels,
            double normalizedLoss,
            double costThreshold,
            double probabilityTemperature) {
        return calculate(rankScores, actualReturns, upProbabilities, directionLabels,
                null, normalizedLoss, costThreshold, probabilityTemperature);
    }

    /**
     * 按交易日横截面分组计算排序指标。
     *
     * @param rankScores 模型排序分数
     * @param actualReturns 实际中性收益
     * @param upProbabilities 原始上涨概率
     * @param directionLabels 方向标签
     * @param groupSizes 按日期排列的横截面样本数量
     * @param normalizedLoss 当前验证损失
     * @param costThreshold 单次组合成本阈值
     * @param probabilityTemperature 概率温度参数
     * @return 横截面排序与概率指标
     */
    public RankingValidationMetrics calculate(
            double[] rankScores,
            double[] actualReturns,
            double[] upProbabilities,
            double[] directionLabels,
            int[] groupSizes,
            double normalizedLoss,
            double costThreshold,
            double probabilityTemperature) {
        if (rankScores == null || actualReturns == null
                || rankScores.length == 0 || rankScores.length != actualReturns.length) {
            return new RankingValidationMetrics(
                    0D, 0D, Double.NEGATIVE_INFINITY, true, null, null, probabilityTemperature);
        }
        CrossSectionMetrics crossSection = crossSectionMetrics(
                rankScores, actualReturns, groupSizes, costThreshold);
        double rankIc = crossSection.rankIc();
        double topDecileNetReturn = crossSection.topDecileNetReturn();
        double selectionScore = rankIc + 0.25D * topDecileNetReturn - 0.10D * normalizedLoss;
        ProbabilityMetrics probabilityMetrics = probabilityMetrics(
                upProbabilities, directionLabels, probabilityTemperature);
        return new RankingValidationMetrics(
                rankIc, topDecileNetReturn, selectionScore, isCollapsed(rankScores),
                probabilityMetrics == null ? null : probabilityMetrics.brierScore(),
                probabilityMetrics == null ? null : probabilityMetrics.brierBaseline(),
                probabilityTemperature);
    }

    private CrossSectionMetrics crossSectionMetrics(
            double[] scores, double[] returns, int[] groupSizes, double costThreshold) {
        if (groupSizes == null || groupSizes.length == 0) {
            return new CrossSectionMetrics(
                    spearman(scores, returns), topDecileReturn(scores, returns) - costThreshold);
        }
        int offset = 0;
        double rankIcTotal = 0D;
        double topReturnTotal = 0D;
        int validGroups = 0;
        for (int groupSize : groupSizes) {
            if (groupSize <= 1 || offset + groupSize > scores.length) {
                offset += Math.max(0, groupSize);
                continue;
            }
            double[] groupScores = java.util.Arrays.copyOfRange(scores, offset, offset + groupSize);
            double[] groupReturns = java.util.Arrays.copyOfRange(returns, offset, offset + groupSize);
            rankIcTotal += spearman(groupScores, groupReturns);
            topReturnTotal += topDecileReturn(groupScores, groupReturns) - costThreshold;
            validGroups++;
            offset += groupSize;
        }
        if (validGroups == 0 || offset != scores.length) {
            return new CrossSectionMetrics(
                    spearman(scores, returns), topDecileReturn(scores, returns) - costThreshold);
        }
        return new CrossSectionMetrics(rankIcTotal / validGroups, topReturnTotal / validGroups);
    }

    /**
     * 在验证集上拟合温度缩放参数。
     *
     * @param probabilities 原始上涨概率
     * @param directionLabels 方向标签，中性样本不参与拟合
     * @return 使验证集负对数似然最小的温度参数
     */
    public double fitProbabilityTemperature(double[] probabilities, double[] directionLabels) {
        if (!sameNonEmptyLength(probabilities, directionLabels)) {
            return 1D;
        }
        double bestTemperature = 1D;
        double bestLoss = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= 50; step++) {
            double temperature = 0.5D + step * 0.05D;
            double loss = binaryLogLoss(probabilities, directionLabels, temperature);
            if (loss < bestLoss) {
                bestLoss = loss;
                bestTemperature = temperature;
            }
        }
        return bestTemperature;
    }

    /**
     * 汇总多个真实 Walk-Forward 测试窗口。
     *
     * @param foldMetrics 各窗口独立训练并评估得到的指标
     * @return Walk-Forward 聚合指标
     */
    public WalkForwardMetrics aggregate(List<RankingValidationMetrics> foldMetrics) {
        if (foldMetrics == null || foldMetrics.isEmpty()) {
            return new WalkForwardMetrics(0, 0D, 0D, 0D, 0D, null, null, true, 0D);
        }
        double meanRankIc = foldMetrics.stream().mapToDouble(RankingValidationMetrics::rankIc).average().orElse(0D);
        double variance = foldMetrics.stream()
                .mapToDouble(item -> Math.pow(item.rankIc() - meanRankIc, 2D)).average().orElse(0D);
        double rankIcIr = Math.sqrt(variance) <= 1.0E-12D ? 0D : meanRankIc / Math.sqrt(variance);
        double positiveRatio = foldMetrics.stream().filter(item -> item.rankIc() > 0D).count()
                / (double) foldMetrics.size();
        double averageTopReturn = foldMetrics.stream()
                .mapToDouble(RankingValidationMetrics::topDecileNetReturn).average().orElse(0D);
        Double brierScore = nullableAverage(foldMetrics.stream()
                .map(RankingValidationMetrics::brierScore).toList());
        Double brierBaseline = nullableAverage(foldMetrics.stream()
                .map(RankingValidationMetrics::brierBaseline).toList());
        boolean collapsed = foldMetrics.stream().anyMatch(RankingValidationMetrics::collapsed);
        return new WalkForwardMetrics(foldMetrics.size(), meanRankIc, rankIcIr, positiveRatio,
                averageTopReturn, brierScore, brierBaseline, collapsed,
                maximumDrawdown(foldMetrics));
    }

    private ProbabilityMetrics probabilityMetrics(
            double[] probabilities, double[] labels, double temperature) {
        if (!sameNonEmptyLength(probabilities, labels)) {
            return null;
        }
        List<Double> calibrated = new ArrayList<>();
        List<Double> binaryLabels = new ArrayList<>();
        for (int index = 0; index < labels.length; index++) {
            if (Math.abs(labels[index] - 0.5D) < 0.25D) {
                continue;
            }
            calibrated.add(calibrate(probabilities[index], temperature));
            binaryLabels.add(labels[index] > 0.5D ? 1D : 0D);
        }
        if (calibrated.isEmpty()) {
            return null;
        }
        double positiveRate = binaryLabels.stream().mapToDouble(Double::doubleValue).average().orElse(0D);
        double brier = 0D;
        double baseline = 0D;
        for (int index = 0; index < calibrated.size(); index++) {
            brier += Math.pow(calibrated.get(index) - binaryLabels.get(index), 2D);
            baseline += Math.pow(positiveRate - binaryLabels.get(index), 2D);
        }
        return new ProbabilityMetrics(brier / calibrated.size(), baseline / calibrated.size());
    }

    private double binaryLogLoss(double[] probabilities, double[] labels, double temperature) {
        double total = 0D;
        int count = 0;
        for (int index = 0; index < labels.length; index++) {
            if (Math.abs(labels[index] - 0.5D) < 0.25D) {
                continue;
            }
            double probability = calibrate(probabilities[index], temperature);
            double label = labels[index] > 0.5D ? 1D : 0D;
            total -= label * Math.log(probability) + (1D - label) * Math.log(1D - probability);
            count++;
        }
        return count == 0 ? Double.POSITIVE_INFINITY : total / count;
    }

    private double calibrate(double probability, double temperature) {
        double safe = Math.max(1.0E-7D, Math.min(1D - 1.0E-7D, probability));
        double logit = Math.log(safe / (1D - safe));
        return 1D / (1D + Math.exp(-logit / Math.max(0.05D, temperature)));
    }

    private boolean sameNonEmptyLength(double[] left, double[] right) {
        return left != null && right != null && left.length > 0 && left.length == right.length;
    }

    private Double nullableAverage(List<Double> values) {
        double total = 0D;
        int count = 0;
        for (Double value : values) {
            if (value != null) {
                total += value;
                count++;
            }
        }
        return count == 0 ? null : total / count;
    }

    private double maximumDrawdown(List<RankingValidationMetrics> foldMetrics) {
        double wealth = 1D;
        double peak = 1D;
        double maximumDrawdown = 0D;
        for (RankingValidationMetrics metrics : foldMetrics) {
            wealth *= Math.max(0.0001D, 1D + metrics.topDecileNetReturn());
            peak = Math.max(peak, wealth);
            maximumDrawdown = Math.max(maximumDrawdown, (peak - wealth) / peak);
        }
        return maximumDrawdown;
    }

    private double spearman(double[] left, double[] right) {
        return pearson(rank(left), rank(right));
    }

    private double[] rank(double[] values) {
        List<Integer> indexes = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            indexes.add(index);
        }
        indexes.sort(Comparator.comparingDouble(index -> values[index]));
        double[] ranks = new double[values.length];
        int position = 0;
        while (position < indexes.size()) {
            int end = position + 1;
            double value = values[indexes.get(position)];
            while (end < indexes.size()
                    && Double.compare(values[indexes.get(end)], value) == 0) {
                end++;
            }
            double averageRank = (position + 1D + end) / 2D;
            for (int cursor = position; cursor < end; cursor++) {
                ranks[indexes.get(cursor)] = averageRank;
            }
            position = end;
        }
        return ranks;
    }

    private double pearson(double[] left, double[] right) {
        double leftMean = mean(left);
        double rightMean = mean(right);
        double numerator = 0D;
        double leftSquares = 0D;
        double rightSquares = 0D;
        for (int index = 0; index < left.length; index++) {
            double leftDeviation = left[index] - leftMean;
            double rightDeviation = right[index] - rightMean;
            numerator += leftDeviation * rightDeviation;
            leftSquares += leftDeviation * leftDeviation;
            rightSquares += rightDeviation * rightDeviation;
        }
        double denominator = Math.sqrt(leftSquares * rightSquares);
        return denominator <= 1.0E-12D ? 0D : numerator / denominator;
    }

    private double topDecileReturn(double[] scores, double[] returns) {
        List<Integer> indexes = new ArrayList<>(scores.length);
        for (int index = 0; index < scores.length; index++) {
            indexes.add(index);
        }
        indexes.sort(Comparator.comparingDouble((Integer index) -> scores[index]).reversed());
        int selected = Math.max(1, (int) Math.ceil(indexes.size() * 0.10D));
        double total = 0D;
        for (int index = 0; index < selected; index++) {
            total += returns[indexes.get(index)];
        }
        return total / selected;
    }

    private boolean isCollapsed(double[] values) {
        double mean = mean(values);
        double variance = 0D;
        for (double value : values) {
            if (!Double.isFinite(value)) {
                return true;
            }
            variance += Math.pow(value - mean, 2D);
        }
        double standardDeviation = Math.sqrt(variance / values.length);
        return standardDeviation < 1.0E-5D;
    }

    private double mean(double[] values) {
        double total = 0D;
        for (double value : values) {
            total += value;
        }
        return values.length == 0 ? 0D : total / values.length;
    }

    /**
     * 横截面排序验证结果。
     *
     * @param rankIc Spearman Rank IC
     * @param topDecileNetReturn Top 10% 成本后收益
     * @param selectionScore 早停和最佳 Epoch 组合分数
     * @param collapsed 是否发生输出塌缩
     * @param brierScore 温度校准后的 Brier Score
     * @param brierBaseline 实际上涨比例常数基线的 Brier Score
     * @param probabilityTemperature 概率温度参数
     */
    public record RankingValidationMetrics(
            double rankIc,
            double topDecileNetReturn,
            double selectionScore,
            boolean collapsed,
            Double brierScore,
            Double brierBaseline,
            double probabilityTemperature) {
    }

    /**
     * Walk-Forward 聚合指标。
     *
     * @param foldCount 有效测试窗口数
     * @param meanRankIc 平均 Rank IC
     * @param rankIcIr Rank ICIR
     * @param positiveRankIcRatio Rank IC 为正的窗口占比
     * @param averageTopDecileNetReturn 平均 Top 10% 成本后收益
     * @param brierScore 平均 Brier Score
     * @param brierBaseline 平均 Brier 基线
     * @param collapsed 任一窗口是否输出塌缩
     * @param maximumDrawdown Top 10% 窗口收益序列最大回撤
     */
    public record WalkForwardMetrics(
            int foldCount,
            double meanRankIc,
            double rankIcIr,
            double positiveRankIcRatio,
            double averageTopDecileNetReturn,
            Double brierScore,
            Double brierBaseline,
            boolean collapsed,
            double maximumDrawdown) {
    }

    private record ProbabilityMetrics(double brierScore, double brierBaseline) {
    }

    private record CrossSectionMetrics(double rankIc, double topDecileNetReturn) {
    }
}
// AI_GENERATE_END-------
