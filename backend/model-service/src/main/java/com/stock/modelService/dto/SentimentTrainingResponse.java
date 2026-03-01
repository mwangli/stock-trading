package com.stock.modelService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 情感分析训练响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentTrainingResponse {

    private boolean success;
    private String message;
    private Integer epochs;
    private Double trainLoss;
    private Double valAccuracy;
    private String modelPath;
    private Integer trainSamples;
    private Integer valSamples;
    private List<Map<String, Object>> details;
}