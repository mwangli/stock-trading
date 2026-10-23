// AI_GENERATE_START---
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单股预测与实际结果对比项。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResultResponse {

    /** 预测明细 ID。 */
    private String id;

    /** 所属预测批次 ID。 */
    private String batchId;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 行业代码。 */
    private Integer industryCode;

    /** 模型文档 ID。 */
    private String modelId;

    /** 模型业务版本。 */
    private String modelVersion;

    /** 基准交易日。 */
    private LocalDate baseTradeDate;

    /** 目标交易日。 */
    private LocalDate targetTradeDate;

    /** 目标交易日是否由工作日规则降级推算。 */
    private Boolean calendarFallback;

    /** 基准日收盘价。 */
    private Double baseClosePrice;

    /** 预测收盘价。 */
    private Double predictedClosePrice;

    /** 预测收益率。 */
    private Double predictedReturn;

    /** 上涨概率。 */
    private Double directionProbability;

    /** 下行风险。 */
    private Double downsideRisk;

    /** 预测收益率横截面百分位。 */
    private Double predictedReturnPercentile;

    /** 上涨概率横截面百分位。 */
    private Double directionProbabilityPercentile;

    /** 实际收盘价。 */
    private Double actualClosePrice;

    /** 实际收益率。 */
    private Double actualReturn;

    /** 预测收益减实际收益。 */
    private Double returnError;

    /** 收益绝对误差。 */
    private Double absoluteError;

    /** 方向是否命中。 */
    private Boolean directionCorrect;

    /** 评估状态。 */
    private String evaluationStatus;

    /** 预测生成时间。 */
    private LocalDateTime predictedAt;

    /** 实际结果评估时间。 */
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END---
