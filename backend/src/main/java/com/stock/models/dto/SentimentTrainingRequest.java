package com.stock.models.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 情感分析训练请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentTrainingRequest {

    private String dataSource = "news";
    private Integer numSamples = -1;
    private Integer epochs;
    private Integer batchSize;
    private Double learningRate;
    private Boolean autoLabel = true;
}

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
    private Map<String, Double> probabilities;
    private String text;
}

/**
 * 训练样本
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrainingSample {

    private String text;
    private Integer label;
    private String source;
}
