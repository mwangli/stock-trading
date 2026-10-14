// AI_GENERATE_START -
package com.stock.modelService.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 情感模型人工标注评估集运行结果。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sentiment_evaluation")
public class SentimentEvaluationEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 本次评估运行标识。 */
    @Column(name = "evaluation_run_id", nullable = false, unique = true, length = 64)
    private String evaluationRunId;

    /** 被评估模型版本。 */
    @Column(name = "model_version", nullable = false, length = 128)
    private String modelVersion;

    /** 人工标注评估集版本。 */
    @Column(name = "dataset_version", nullable = false, length = 128)
    private String datasetVersion;

    /** 评估状态：PASSED、OBSERVE 或 REJECTED。 */
    @Column(nullable = false, length = 32)
    private String status;

    /** 评估样本数量。 */
    @Column(name = "sample_count", nullable = false)
    private int sampleCount;

    /** 预测正确样本数量。 */
    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    /** 三分类准确率。 */
    @Column(nullable = false)
    private double accuracy;

    /** 三分类 Macro-F1。 */
    @Column(name = "macro_f1", nullable = false)
    private double macroF1;

    /** 负面类别召回率。 */
    @Column(name = "negative_recall", nullable = false)
    private double negativeRecall;

    /** 期望校准误差。 */
    @Column(name = "expected_calibration_error", nullable = false)
    private double expectedCalibrationError;

    /** 单条推理 P95 耗时，单位毫秒。 */
    @Column(name = "p95_latency_ms", nullable = false)
    private double p95LatencyMs;

    /** 面向运维页面的评估结论。 */
    @Column(nullable = false, length = 500)
    private String conclusion;

    /** 评估完成时间。 */
    @Column(name = "evaluated_at", nullable = false)
    private LocalDateTime evaluatedAt;
}
// AI_GENERATE_END -
