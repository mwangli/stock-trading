// AI_GENERATE_START --
package com.stock.strategyAnalysis.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票候选排名快照。
 * 保存候选分数以及预测、模型、策略和交易日追溯信息。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockRanking {

    /** 候选批次 ID。 */
    private String candidateBatchId;

    /** 来源预测批次 ID。 */
    private String predictionBatchId;

    /** LSTM 模型文档 ID。 */
    private String modelId;

    /** LSTM 模型业务版本。 */
    private String modelVersion;

    /** 情感模型版本。 */
    private String sentimentModelVersion;

    /** 策略配置版本。 */
    private String strategyConfigVersion;

    /** 模型输入基准交易日。 */
    private LocalDate baseTradeDate;

    /** 候选目标交易日。 */
    private LocalDate targetTradeDate;

    /** 本批候选允许使用信息的截止时间。 */
    private LocalDateTime informationCutoffAt;

    /** 候选状态。 */
    private String status;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 页面展示的股票价格，取预测基准日收盘价。 */
    private Double currentPrice;

    /** LSTM 归一化分数。 */
    private double lstmScore;

    /** 情感聚合分数。 */
    private double sentimentScore;

    /** 综合候选分数。 */
    private double totalScore;

    /** 批次内排名。 */
    private int rank;

    /** 候选生成时间。 */
    private LocalDateTime calculateTime;

    /** 候选原因。 */
    private String reason;
}
// AI_GENERATE_END --
