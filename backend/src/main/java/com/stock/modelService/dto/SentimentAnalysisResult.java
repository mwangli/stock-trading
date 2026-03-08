package com.stock.modelService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 情感分析结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SentimentAnalysisResult {

    private String label;
    private double score;
    private double confidence;
    private double normalizedScore; // 0-100分归一化得分
    private Map<String, Double> probabilities;
    private String text;
}