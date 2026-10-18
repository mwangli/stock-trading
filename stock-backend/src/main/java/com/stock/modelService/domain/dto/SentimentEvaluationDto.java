// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 情感模型独立评估结果 DTO。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentEvaluationDto {

    /** 评估运行标识。 */
    private String evaluationRunId;

    /** 模型版本。 */
    private String modelVersion;

    /** 人工标注评估集版本。 */
    private String datasetVersion;

    /** 评估状态。 */
    private String status;

    /** 评估样本数量。 */
    private int sampleCount;

    /** 正确样本数量。 */
    private int correctCount;

    /** 三分类准确率。 */
    private Double accuracy;

    /** 三分类 Macro-F1。 */
    private Double macroF1;

    /** 负面类别召回率。 */
    private Double negativeRecall;

    /** 正面类别召回率。 */
    private Double positiveRecall;

    /** 模型预测为中性的样本占比。 */
    private Double predictedNeutralRatio;

    /** 评估集覆盖的事件类型占要求事件类型的比例。 */
    private Double eventTypeCoverage;

    /** 评估集标注为可交易动作的新闻占比。 */
    private Double actionableNewsRatio;

    /** 影响方向预测准确率。 */
    private Double directionAccuracy;

    /** 文本影响强度基线 MAE。 */
    private Double impactMagnitudeMae;

    /** 事件类型 Macro-F1，当前为规则基线评估。 */
    private Double eventTypeMacroF1;

    /** T+1 超额收益 IC，未接入历史回放时为空。 */
    private Double t1ExcessReturnIc;

    /** T+3 超额收益 IC，未接入历史回放时为空。 */
    private Double t3ExcessReturnIc;

    /** T+5 超额收益 IC，未接入历史回放时为空。 */
    private Double t5ExcessReturnIc;

    /** Top-K 新闻命中率，未接入历史回放时为空。 */
    private Double topKNewsHitRate;

    /** Top-K 股票组合收益，未接入历史回放时为空。 */
    private Double topKPortfolioReturn;

    /** 最大回撤，未接入历史回放时为空。 */
    private Double maxDrawdown;

    /** 换手率，未接入历史回放时为空。 */
    private Double turnoverRate;

    /** 扣除交易成本后的收益，未接入历史回放时为空。 */
    private Double netReturnAfterCosts;

    /** 新闻因子相对 LSTM 基线的增量收益，未接入历史回放时为空。 */
    private Double lstmIncrementalReturn;

    /** 期望校准误差。 */
    private Double expectedCalibrationError;

    /** 单条推理 P95 耗时，单位毫秒。 */
    private Double p95LatencyMs;

    /** 评估结论。 */
    private String conclusion;

    /** 评估完成时间。 */
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END -
