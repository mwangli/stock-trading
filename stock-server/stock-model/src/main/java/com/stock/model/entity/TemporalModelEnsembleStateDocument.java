// AI_GENERATE_START ----
package com.stock.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * Ranking LSTM 与 MASTER 的滚动指标、连续权重和自动选择状态。
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

    /** 状态结构版本；不匹配时直接重新初始化。 */
    private String schemaVersion;

    /** 当前 Ranking LSTM 生产基础模型文档 ID。 */
    private String rankingLstmModelId;

    /** 当前自动注册的 MASTER 候选模型文档 ID，可为空。 */
    private String masterModelId;

    /** Ranking LSTM 当前权重，范围 0 到 1。 */
    private double rankingLstmWeight;

    /** MASTER 当前权重，范围 0 到 1。 */
    private double masterWeight;

    /** 目标 Ranking LSTM 权重，范围 0 到 1。 */
    private double targetRankingLstmWeight;

    /** 目标 MASTER 权重，范围 0 到 1。 */
    private double targetMasterWeight;

    /** 当前组合综合质量分。 */
    private Double currentCombinationQualityScore;

    /** 最近窗口内最优组合综合质量分。 */
    private Double bestCombinationQualityScore;

    /** 自动选择状态：OBSERVING、ACTIVE、DEGRADED 或 UNAVAILABLE。 */
    private String status;

    /** 权重版本；仅在实际权重变化时递增。 */
    private long weightVersion;

    /** 当前权重最近生效的交易日。 */
    private LocalDate effectiveTradeDate;

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
// AI_GENERATE_END ----
