package com.stock.models.dto;

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

    /**
     * 股票代码（逗号分隔，多个股票联合训练）
     */
    private String stockCodes;

    /**
     * 训练数据天数
     */
    @Builder.Default
    private Integer days = 365;

    /**
     * 训练轮次（可选，覆盖默认配置）
     */
    private Integer epochs;

    /**
     * 批次大小（可选）
     */
    private Integer batchSize;

    /**
     * 学习率（可选）
     */
    private Double learningRate;
}