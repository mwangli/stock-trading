package com.stock.models.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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