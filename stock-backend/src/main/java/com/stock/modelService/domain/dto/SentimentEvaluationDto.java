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
