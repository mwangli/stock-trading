package com.stock.modelService.dto;

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

    @Builder.Default
    private String dataSource = "news";
    @Builder.Default
    private Integer numSamples = -1;
    private Integer epochs;
    private Integer batchSize;
    private Double learningRate;
    @Builder.Default
    private Boolean autoLabel = true;
}