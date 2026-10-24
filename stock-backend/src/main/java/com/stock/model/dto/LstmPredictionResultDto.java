// AI_GENERATE_START------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * LSTM 单只股票多任务预测结果 DTO。
 * 提供下一交易日收益率、方向概率、下行风险及推导后的预测收盘价，
 * 供策略排序、风险过滤和审计追踪使用。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LstmPredictionResultDto {

    /**
     * 股票代码。
     */
    private String stockCode;

    /**
     * 推理输入最后一条行情的交易日。
     */
    private LocalDate baseTradeDate;

    /**
     * 使用模型推导的下一交易日收盘价，单位与行情源一致。
     */
    private Double predictedClosePrice;

    /**
     * 最新一个交易日的实际收盘价，单位与行情源一致。
     */
    private Double lastClosePrice;

    /**
     * 预测下一交易日收益率，0.01 表示上涨 1%。
     */
    private Double predictedChangeRatio;

    /**
     * 预测下一交易日上涨概率，范围为 0 到 1。
     */
    private Double directionProbability;

    /**
     * 预测下一交易日下行风险，范围为 0 到 1。
     */
    private Double downsideRisk;

    /** `v5` 横截面排序原始分数。 */
    private Double rankScore;

    /** 预测市场和行业中性后的超额收益率。 */
    private Double expectedExcessReturn;

    /** 经模型制品参数校准后的上涨概率。 */
    private Double calibratedUpProbability;

    /** 预测下行风险分位值，范围为 0 到 1。 */
    private Double downsideQuantile;

    /** 模型架构类型。 */
    private String architectureType;

    /** 推理使用的特征契约版本。 */
    private String featureSchemaVersion;

    /**
     * 使用的模型文档 ID 或本地模型路径。
     */
    private String modelId;
}
// AI_GENERATE_END------
