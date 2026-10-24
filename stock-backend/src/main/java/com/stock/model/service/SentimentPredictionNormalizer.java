// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.model.dto.SentimentModelInput;
import com.stock.model.vo.SentimentAnalysisResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 三分类情感输出归一化服务。
 * 统一执行标签映射、分块概率平均、温度缩放、熵和弃权计算。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Service
public class SentimentPredictionNormalizer {

    private static final List<String> LABELS = List.of("negative", "neutral", "positive");
    private static final double ABSTAIN_CONFIDENCE = 0.55D;
    private static final double ABSTAIN_MARGIN = 0.10D;

    /**
     * 将多个文本块的概率统一为一个可审计结果。
     *
     * @param chunkProbabilities 每个文本块的三分类概率
     * @param input 统一模型输入
     * @param temperature 校准温度，大于 0；未完成校准时使用 1
     * @return 统一情感结果
     */
    public SentimentAnalysisResult normalize(
            List<Map<String, Double>> chunkProbabilities,
            SentimentModelInput input,
            double temperature) {
        if (chunkProbabilities == null || chunkProbabilities.isEmpty()) {
            throw new IllegalArgumentException("情感模型输出概率不能为空");
        }
        Map<String, Double> averaged = new LinkedHashMap<>();
        LABELS.forEach(label -> averaged.put(label, 0D));
        for (Map<String, Double> probabilities : chunkProbabilities) {
            LABELS.forEach(label -> averaged.compute(label,
                    (ignored, value) -> value + validProbability(probabilities.get(label))));
        }
        averaged.replaceAll((label, value) -> value / chunkProbabilities.size());
        Map<String, Double> calibrated = applyTemperature(averaged, temperature);
        List<Map.Entry<String, Double>> ordered = new ArrayList<>(calibrated.entrySet());
        ordered.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
        String label = ordered.get(0).getKey();
        double confidence = ordered.get(0).getValue();
        double margin = confidence - ordered.get(1).getValue();
        double entropy = 0D;
        for (double probability : calibrated.values()) {
            if (probability > 0D) {
                entropy -= probability * Math.log(probability);
            }
        }
        double score = calibrated.get("positive") - calibrated.get("negative");
        boolean abstained = confidence < ABSTAIN_CONFIDENCE || margin < ABSTAIN_MARGIN;
        return SentimentAnalysisResult.builder()
                .label(label)
                .score(Math.max(-1D, Math.min(1D, score)))
                .normalizedScore(Math.max(0D, Math.min(100D, 50D + score * 50D)))
                .confidence(averaged.get(label))
                .calibratedConfidence(confidence)
                .entropy(entropy / Math.log(LABELS.size()))
                .probabilityMargin(margin)
                .abstained(abstained)
                .chunkCount(input.getChunks().size())
                .textCoverage(input.getTextCoverage())
                .probabilities(calibrated)
                .text(String.join("\n", input.getChunks()))
                .build();
    }

    private Map<String, Double> applyTemperature(Map<String, Double> probabilities, double temperature) {
        double safeTemperature = temperature > 0D && Double.isFinite(temperature) ? temperature : 1D;
        Map<String, Double> values = new LinkedHashMap<>();
        double total = 0D;
        for (String label : LABELS) {
            double value = Math.pow(Math.max(1.0E-12D, probabilities.get(label)), 1D / safeTemperature);
            values.put(label, value);
            total += value;
        }
        double denominator = total;
        values.replaceAll((label, value) -> value / denominator);
        return values;
    }

    private double validProbability(Double value) {
        return value == null || !Double.isFinite(value) ? 0D : Math.max(0D, Math.min(1D, value));
    }
}
// AI_GENERATE_END -
