// AI_GENERATE_START------
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
    /** 温度缩放后的校准置信度，取值范围为 0 到 1。 */
    private double calibratedConfidence;
    /** 归一化信息熵，取值范围为 0 到 1，值越高表示越不确定。 */
    private double entropy;
    /** 最高与次高类别概率差，取值范围为 0 到 1。 */
    private double probabilityMargin;
    /** 是否因置信度或概率间隔不足而弃权。 */
    private boolean abstained;
    /** 本次推理使用的文本分块数量，范围为 1 到 3。 */
    private int chunkCount;
    /** 实际送入模型的文本覆盖率，取值范围为 0 到 1。 */
    private double textCoverage;
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

// AI_GENERATE_END------
