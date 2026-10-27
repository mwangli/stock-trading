// AI_GENERATE_START----
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 训练运行列表项。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingRunResponse {

    /** 训练运行 ID。 */
    private String id;

    /** 模型逻辑名称。 */
    private String modelName;

    /** 训练运行状态。 */
    private String status;

    /** 触发来源。 */
    private String triggerSource;

    /** 触发操作人。 */
    private String triggeredBy;

    /** 请求参数 JSON。 */
    private String requestedConfigJson;

    /** 实际生效参数 JSON。 */
    private String effectiveConfigJson;

    /** 门禁结果 JSON。 */
    private String gateResultJson;

    /** 开始时间。 */
    private LocalDateTime startedAt;

    /** 结束时间。 */
    private LocalDateTime finishedAt;

    /** 候选模型版本 ID。 */
    private String candidateModelVersionId;

    /** 本次训练使用的训练数据快照 ID。 */
    private String datasetSnapshotId;

    /** 失败原因。 */
    private String errorMessage;

    /** 实际参与训练并产生有效样本的股票数量。 */
    private Integer trainedStocks;

    /** 当前训练阶段。 */
    private String progressStage;

    /** 当前训练阶段说明。 */
    private String progressMessage;

    /** 当前完成比例，范围 0-100。 */
    private Integer progressPercent;

    /** 当前已完成训练轮次。 */
    private Integer currentEpoch;

    /** 本次训练计划总轮次。 */
    private Integer totalEpochs;

    /** 当前已耗时，单位秒。 */
    private Long elapsedSeconds;

    /** 预计剩余时间，单位秒。 */
    private Long estimatedRemainingSeconds;

    /** 最近一次进度更新时间。 */
    private LocalDateTime lastProgressAt;

    /** 训练样本数量。 */
    private Integer trainSamples;

    /** 验证样本数量。 */
    private Integer valSamples;

    /** 最佳训练损失。 */
    private Double trainLoss;

    /** 最佳验证损失。 */
    private Double valLoss;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END----
