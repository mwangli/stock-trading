package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 情感分析结果 DTO
 * <p>
 * 封装情感标签、得分、置信度以及概率分布等信息，
 * 作为对外 API 的统一返回结构，避免直接暴露 Map。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalyzeResultDto {

    /**
     * 是否调用成功
     */
    private boolean success;

    /**
     * 提示信息（错误信息或说明）
     */
    private String message;

    /**
     * 情感标签：positive / neutral / negative
     */
    private String label;

    /**
     * 原始情感分数（正负值）
     */
    private Double score;

    /**
     * 归一化情感得分（0-100）
     */
    private Double normalizedScore;

    /**
     * 置信度（预测类别的概率）
     */
    private Double confidence;

    /**
     * 各情感类别的概率分布
     */
    private Map<String, Double> probabilities;

    /**
     * 原始文本内容
     */
    private String text;

    /**
     * 是否实际加载了预训练模型
     */
    private Boolean modelLoaded;

    /**
     * 关联的股票代码（按股票分析时使用，可为空）
     */
    private String stockCode;
}

