// AI_GENERATE_START----
package com.stock.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 情感分析结果
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SentimentAnalysisResult {

    /**
     * 情感分类标签。
     */
    private String label;
    /**
     * 模型输出的原始情感得分。
     */
    private double score;
    /**
     * 情感分类置信度，取值范围为 0 到 1。
     */
    private double confidence;
    /**
     * 归一化情感得分，取值范围为 0 到 100。
     */
    private double normalizedScore;
    /**
     * 各情感分类标签对应的概率。
     */
    private Map<String, Double> probabilities;
    /**
     * 参与情感分析的原始文本。
     */
    private String text;
}

// AI_GENERATE_END----
