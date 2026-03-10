package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LSTM 单只股票预测结果 DTO
 * <p>
 * 使用最新的 LSTM 模型与价格数据，对指定股票代码进行下一周期价格预测，
 * 提供原始预测价格、最新收盘价以及相对涨跌幅信息，供前端和策略模块直接消费。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LstmPredictionResultDto {

    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 使用 LSTM 模型预测的下一交易日收盘价（原始价格，单位与源数据一致）
     */
    private Double predictedClosePrice;

    /**
     * 最新一个交易日的实际收盘价
     */
    private Double lastClosePrice;

    /**
     * 预测涨跌幅（(predicted - lastClose) / lastClose）
     */
    private Double predictedChangeRatio;

    /**
     * 使用的模型文档 ID（MongoDB _id，便于追踪与调试）
     */
    private String modelId;
}

