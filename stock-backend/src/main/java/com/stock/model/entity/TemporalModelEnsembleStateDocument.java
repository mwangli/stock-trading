// AI_GENERATE_START ---
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Ranking LSTM 与 MASTER 的滚动指标、动态权重和生产启用状态。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Document(collection = "temporal_model_ensemble_states")
public class TemporalModelEnsembleStateDocument {

    /** 固定融合状态主键。 */
    @Id
    private String id;

    /** 当前自动注册的 MASTER 候选模型文档 ID，可为空。 */
    private String masterModelId;

    /** Ranking LSTM 当前权重，范围 0 到 1。 */
    private double rankingLstmWeight;

    /** MASTER 当前权重，范围 0 到 1。 */
    private double masterWeight;

    /** 是否允许生产候选应用融合。 */
    private boolean productionEnabled;

    /** Ranking LSTM 滚动 Rank IC。 */
    private Double rankingLstmRankIc;

    /** MASTER 滚动 Rank IC。 */
    private Double masterRankIc;

    /** Ranking LSTM 滚动 ICIR。 */
    private Double rankingLstmIcIr;

    /** MASTER 滚动 ICIR。 */
    private Double masterIcIr;

    /** Ranking LSTM 扣成本收益。 */
    private Double rankingLstmNetReturn;

    /** MASTER 扣成本收益。 */
    private Double masterNetReturn;

    /** Ranking LSTM 最大回撤。 */
    private Double rankingLstmMaxDrawdown;

    /** MASTER 最大回撤。 */
    private Double masterMaxDrawdown;

    /** Ranking LSTM 输出标准差。 */
    private Double rankingLstmOutputStd;

    /** MASTER 输出标准差。 */
    private Double masterOutputStd;

    /** Ranking LSTM 综合质量分。 */
    private Double rankingLstmQualityScore;

    /** MASTER 综合质量分。 */
    private Double masterQualityScore;

    /** 权重决策原因。 */
    private String decisionReason;

    /** 当前滚动窗口包含的已评估交易日数。 */
    private int evaluatedTradingDays;

    /** 当前滚动窗口包含的已评估股票样本数。 */
    private int evaluatedSamples;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END ---
