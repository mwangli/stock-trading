// AI_GENERATE_START ---
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ranking LSTM 与 MASTER 影子评估和生产融合状态。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemporalModelEnsembleStatusResponse {

    /** 是否启用 MASTER 影子预测。 */
    private boolean masterShadowEnabled;

    /** 是否配置允许生产融合。 */
    private boolean productionBlendConfigured;

    /** 当前滚动指标是否允许生产融合。 */
    private boolean productionEnabled;

    /** MASTER 候选模型文档 ID，可为空。 */
    private String masterModelId;

    /** Ranking LSTM 当前融合权重，范围 0 到 1。 */
    private double rankingLstmWeight;

    /** MASTER 当前融合权重，范围 0 到 1。 */
    private double masterWeight;

    /** 已进入滚动窗口的评估交易日数量。 */
    private int evaluatedTradingDays;

    /** 已进入滚动窗口的评估股票样本数量。 */
    private int evaluatedSamples;

    /** Ranking LSTM 滚动 Rank IC，可为空。 */
    private Double rankingLstmRankIc;

    /** MASTER 滚动 Rank IC，可为空。 */
    private Double masterRankIc;

    /** Ranking LSTM 滚动 ICIR，可为空。 */
    private Double rankingLstmIcIr;

    /** MASTER 滚动 ICIR，可为空。 */
    private Double masterIcIr;

    /** Ranking LSTM 综合质量分，可为空。 */
    private Double rankingLstmQualityScore;

    /** MASTER 综合质量分，可为空。 */
    private Double masterQualityScore;

    /** 当前融合门禁或权重决策原因。 */
    private String decisionReason;

    /** 最近一次状态更新时间，可为空。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END ---
