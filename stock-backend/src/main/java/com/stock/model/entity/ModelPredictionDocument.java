// AI_GENERATE_START--------
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 单股模型预测与实际结果对比文档。
 * 保存不可变预测输出，并在目标交易日行情到达后回填评估结果。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Document(collection = "model_predictions")
@CompoundIndexes({
        @CompoundIndex(name = "uk_prediction_batch_stock",
                def = "{'batchId': 1, 'stockCode': 1}", unique = true),
        @CompoundIndex(name = "idx_prediction_batch_status", def = "{'batchId': 1, 'evaluationStatus': 1}"),
        @CompoundIndex(name = "idx_prediction_batch_return", def = "{'batchId': 1, 'predictedReturn': -1}")
})
public class ModelPredictionDocument {

    /** MongoDB 文档 ID。 */
    @Id
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

    /** 模型业务版本号。 */
    private String modelVersion;

    /** 推理使用的基准交易日。 */
    private LocalDate baseTradeDate;

    /** 需要回填实际值的目标交易日。 */
    private LocalDate targetTradeDate;

    /** 目标交易日是否由工作日规则降级推算。 */
    private Boolean calendarFallback;

    /** 基准日实际收盘价。 */
    private Double baseClosePrice;

    /** 预测目标日收盘价。 */
    private Double predictedClosePrice;

    /** 预测目标日收益率。 */
    private Double predictedReturn;

    /** 预测上涨概率。 */
    private Double directionProbability;

    /** 预测下行风险。 */
    private Double downsideRisk;

    /** 预测收益率在本批次横截面中的百分位，范围 0 到 1。 */
    private Double predictedReturnPercentile;

    /** 上涨概率在本批次横截面中的百分位，范围 0 到 1。 */
    private Double directionProbabilityPercentile;

    /** `v5` 横截面排序原始分数。 */
    private Double rankScore;

    /** 排序分数在预测批次横截面中的百分位，范围 0 到 1。 */
    private Double crossSectionPercentile;

    /** 预测市场和行业中性后的超额收益率。 */
    private Double expectedExcessReturn;

    /** 经模型制品参数校准后的上涨概率。 */
    private Double calibratedUpProbability;

    /** 预测下行风险分位值。 */
    private Double downsideQuantile;

    /** 模型架构类型。 */
    private String architectureType;

    /** 推理使用的特征契约版本。 */
    private String featureSchemaVersion;

    /** 目标日实际收盘价。 */
    private Double actualClosePrice;

    /** 目标日实际收益率。 */
    private Double actualReturn;

    /** 预测收益减实际收益。 */
    private Double returnError;

    /** 收益误差绝对值。 */
    private Double absoluteError;

    /** 收益误差平方。 */
    private Double squaredError;

    /** 预测方向是否命中。 */
    private Boolean directionCorrect;

    /** 评估状态：PENDING、EVALUATED 或 NO_MARKET_DATA。 */
    private String evaluationStatus;

    /** 预测生成时间。 */
    private LocalDateTime predictedAt;

    /** 实际结果评估时间。 */
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END--------
