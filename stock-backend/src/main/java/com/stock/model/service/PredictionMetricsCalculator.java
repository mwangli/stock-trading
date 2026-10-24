// AI_GENERATE_START---
package com.stock.model.service;

import com.stock.model.entity.ModelPredictionDocument;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 模型预测统计指标计算器。
 *
 * 负责无状态的离线评估算法，不访问数据库，也不参与预测批次状态编排。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class PredictionMetricsCalculator {

    /**
     * 计算样本标准差。
     *
     * @param values 数值样本
     * @return 标准差，样本不足两个时返回 0
     */
    public double standardDeviation(double[] values) {
        if (values.length < 2) {
            return 0D;
        }
        double mean = Arrays.stream(values).average().orElse(0D);
        return Math.sqrt(Arrays.stream(values)
                .map(value -> Math.pow(value - mean, 2D)).average().orElse(0D));
    }

    /**
     * 计算上涨概率的 ROC AUC。
     *
     * @param samples 同时包含预测概率和实际收益的有效样本
     * @return ROC AUC，实际标签只有单一类别时返回空值
     */
    public Double areaUnderRoc(List<ModelPredictionDocument> samples) {
        long positiveCount = samples.stream().filter(item -> item.getActualReturn() > 0D).count();
        long negativeCount = samples.size() - positiveCount;
        if (positiveCount == 0L || negativeCount == 0L) {
            return null;
        }
        List<ModelPredictionDocument> sorted = samples.stream()
                .sorted(Comparator.comparingDouble(ModelPredictionDocument::getDirectionProbability))
                .toList();
        double positiveRankSum = 0D;
        int position = 0;
        while (position < sorted.size()) {
            int end = position + 1;
            while (end < sorted.size() && Double.compare(
                    sorted.get(position).getDirectionProbability(),
                    sorted.get(end).getDirectionProbability()) == 0) {
                end++;
            }
            double averageRank = (position + end - 1) / 2D + 1D;
            for (int index = position; index < end; index++) {
                if (sorted.get(index).getActualReturn() > 0D) {
                    positiveRankSum += averageRank;
                }
            }
            position = end;
        }
        double baselineRankSum = positiveCount * (positiveCount + 1D) / 2D;
        return (positiveRankSum - baselineRankSum) / (positiveCount * (double) negativeCount);
    }

    /**
     * 计算二分类概率的 Brier Score。
     *
     * @param samples 有效概率样本
     * @return Brier Score，样本为空时返回空值
     */
    public Double brierScore(List<ModelPredictionDocument> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        return samples.stream().mapToDouble(item -> {
            double label = item.getActualReturn() > 0D ? 1D : 0D;
            double error = item.getDirectionProbability() - label;
            return error * error;
        }).average().orElse(0D);
    }

    /**
     * 使用十个等宽概率桶计算期望校准误差。
     *
     * @param samples 有效概率样本
     * @return ECE，样本为空时返回空值
     */
    public Double expectedCalibrationError(List<ModelPredictionDocument> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        double weightedError = 0D;
        for (int bucket = 0; bucket < 10; bucket++) {
            final int currentBucket = bucket;
            double lowerBound = currentBucket / 10D;
            double upperBound = (currentBucket + 1) / 10D;
            List<ModelPredictionDocument> bucketSamples = samples.stream()
                    .filter(item -> currentBucket == 9
                            ? item.getDirectionProbability() >= lowerBound
                                    && item.getDirectionProbability() <= upperBound
                            : item.getDirectionProbability() >= lowerBound
                                    && item.getDirectionProbability() < upperBound)
                    .toList();
            if (bucketSamples.isEmpty()) {
                continue;
            }
            double predictedMean = bucketSamples.stream()
                    .mapToDouble(ModelPredictionDocument::getDirectionProbability).average().orElse(0D);
            double actualMean = bucketSamples.stream()
                    .filter(item -> item.getActualReturn() > 0D).count() / (double) bucketSamples.size();
            weightedError += Math.abs(predictedMean - actualMean) * bucketSamples.size() / samples.size();
        }
        return weightedError;
    }

    /**
     * 将数值转换为平均秩，重复值共享同一秩。
     *
     * @param values 原始数值
     * @return 平均秩数组
     */
    public double[] rank(double[] values) {
        Integer[] indexes = new Integer[values.length];
        for (int index = 0; index < values.length; index++) {
            indexes[index] = index;
        }
        Arrays.sort(indexes, Comparator.comparingDouble(index -> values[index]));
        double[] ranks = new double[values.length];
        int position = 0;
        while (position < indexes.length) {
            int end = position + 1;
            while (end < indexes.length
                    && Double.compare(values[indexes[position]], values[indexes[end]]) == 0) {
                end++;
            }
            double averageRank = (position + end - 1) / 2D + 1D;
            for (int index = position; index < end; index++) {
                ranks[indexes[index]] = averageRank;
            }
            position = end;
        }
        return ranks;
    }

    /**
     * 计算两个等长数组的 Pearson 相关系数。
     *
     * @param left 左侧样本
     * @param right 右侧样本
     * @return 相关系数，样本不足或方差为零时返回 0
     */
    public double correlation(double[] left, double[] right) {
        if (left.length != right.length || left.length < 2) {
            return 0D;
        }
        double leftAverage = Arrays.stream(left).average().orElse(0D);
        double rightAverage = Arrays.stream(right).average().orElse(0D);
        double covariance = 0D;
        double leftVariance = 0D;
        double rightVariance = 0D;
        for (int index = 0; index < left.length; index++) {
            double leftDelta = left[index] - leftAverage;
            double rightDelta = right[index] - rightAverage;
            covariance += leftDelta * rightDelta;
            leftVariance += leftDelta * leftDelta;
            rightVariance += rightDelta * rightDelta;
        }
        double denominator = Math.sqrt(leftVariance * rightVariance);
        return denominator == 0D ? 0D : covariance / denominator;
    }
}
// AI_GENERATE_END---
