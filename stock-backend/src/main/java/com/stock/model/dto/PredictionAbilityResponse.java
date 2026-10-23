// AI_GENERATE_START---
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/**
 * 模型能力评估响应。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionAbilityResponse {

    /** 预测批次 ID。 */
    private String batchId;

    /** 模型文档 ID。 */
    private String modelId;

    /** 模型业务版本。 */
    private String modelVersion;

    /** 预测基准交易日。 */
    private LocalDate baseTradeDate;

    /** 预测目标交易日。 */
    private LocalDate targetTradeDate;

    /** 成功预测数量。 */
    private int predictedCount;

    /** 已评估数量。 */
    private int evaluatedCount;

    /** 预测覆盖率。 */
    private Double coverageRate;

    /** 评估完成率。 */
    private Double evaluationRate;

    /** 方向准确率。 */
    private Double directionAccuracy;

    /** 实际上涨比例。 */
    private Double actualUpRate;

    /** 常数方向基线准确率。 */
    private Double constantBaselineAccuracy;

    /** 方向上涨概率的 ROC AUC；实际标签只有单一类别时为空。 */
    private Double directionAuc;

    /** 上涨概率 Brier Score，越低越好。 */
    private Double brierScore;

    /** 固定上涨比例的 Brier 基线。 */
    private Double brierBaseline;

    /** 上涨概率期望校准误差。 */
    private Double expectedCalibrationError;

    /** 概率校准有效样本数。 */
    private Integer calibrationSampleCount;

    /** 上涨概率标准差。 */
    private Double directionProbabilityStd;

    /** 预测收益率标准差。 */
    private Double predictedReturnStd;

    /** 最近滚动窗口批次数。 */
    private Integer rollingWindowBatchCount;

    /** 最近滚动窗口有效样本数。 */
    private Integer rollingWindowSampleCount;

    /** 最近滚动窗口平均排序 IC。 */
    private Double rollingAverageSpearmanRankIc;

    /** 最近滚动窗口 Top 10% 平均收益。 */
    private Double rollingAverageTopDecileReturn;

    /** 收益率平均绝对误差。 */
    private Double returnMae;

    /** 收益率均方根误差。 */
    private Double returnRmse;

    /** Pearson 横截面相关系数。 */
    private Double pearsonIc;

    /** Spearman 排序相关系数。 */
    private Double spearmanRankIc;

    /** Top 10% 股票实际平均收益率。 */
    private Double topDecileAverageReturn;

    /** Top 10% 股票上涨命中率。 */
    private Double topDecileDirectionAccuracy;

    /** 高置信度方向准确率。 */
    private Double highConfidenceAccuracy;

    /** 高下行风险样本实际下跌识别率。 */
    private Double downsideRiskHitRate;

    /** 能力等级。 */
    private String abilityLevel;

    /** 是否建议优化迭代模型。 */
    private boolean iterationRecommended;

    /** 能力结论说明。 */
    private String abilityMessage;

    /** 交易门禁状态。 */
    private String tradingEligibility;

    /** 交易门禁原因。 */
    private String tradingEligibilityReason;
}
// AI_GENERATE_END---
