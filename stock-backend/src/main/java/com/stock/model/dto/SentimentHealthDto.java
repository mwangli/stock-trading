// AI_GENERATE_START-----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 情感模型服务和大制品健康状态 DTO。
 * 区分制品可用、摘要已验证和真实模型已加载三个状态。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentHealthDto {

    /** 服务状态。 */
    private String status;

    /** 服务名称。 */
    private String service;

    /** 真实模型是否已加载。 */
    private boolean modelLoaded;

    /** 最近一次成功加载时间。 */
    private LocalDateTime lastLoadedTime;

    /** 配置版本的制品是否满足轻量检查。 */
    private boolean artifactAvailable;

    /** 是否存在合法 manifest。 */
    private boolean manifestPresent;

    /** 是否已完成全部文件 SHA-256 校验。 */
    private boolean checksumsVerified;

    /** 当前配置或实际加载的模型版本。 */
    private String modelVersion;

    /** 实际版本目录。 */
    private String artifactPath;

    /** 清单文件总字节数。 */
    private Long totalSizeBytes;

    /** manifest 文件 SHA-256。 */
    private String manifestSha256;

    /** 人工复核评估集版本。 */
    private String evaluationDatasetVersion;

    /** 评估样本数量。 */
    private Integer evaluationSampleCount;

    /** 三分类 Macro-F1。 */
    private Double macroF1;

    /** 负面类别召回率。 */
    private Double negativeRecall;

    /** 期望校准误差 ECE。 */
    private Double expectedCalibrationError;

    /** 单条推理 P95 耗时，单位毫秒。 */
    private Double p95LatencyMs;

    /** 离线评估结论。 */
    private String evaluationConclusion;

    /** Mengzi 官方基础编码器是否完成清单和 SHA-256 校验。 */
    private boolean mengziEncoderVerified;

    /** Mengzi 官方基础编码器目录。 */
    private String mengziEncoderPath;

    /** Mengzi 三分类候选状态，不得与编码器加载状态混淆。 */
    private String mengziClassifierStatus;

    /** 最近一次加载或检查错误。 */
    private String lastError;
}
// AI_GENERATE_END-----
