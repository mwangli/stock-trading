package com.stock.modelService.domain.param;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LSTM 训练请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingRequest {

    private String stockCodes;
    @Builder.Default
    private Integer days = 365;
    private Integer epochs;
    private Integer batchSize;
    private Double learningRate;
}
