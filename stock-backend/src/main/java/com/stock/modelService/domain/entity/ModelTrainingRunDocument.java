// AI_GENERATE_START -----
package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 模型训练运行记录。
 * 保存训练触发、门禁、执行状态和候选模型版本，供运维页面查询与审计。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Document(collection = "model_training_runs")
@CompoundIndex(name = "idx_training_model_created", def = "{'modelName': 1, 'createdAt': -1}")
public class ModelTrainingRunDocument {

    @Id
    private String id;

    private String modelName;

    private String status;

    private String triggerSource;

    private String triggeredBy;

    private String requestedConfigJson;

    private String effectiveConfigJson;

    private String gateResultJson;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String candidateModelVersionId;

    /** 本次训练使用的训练数据快照 ID。 */
    private String datasetSnapshotId;

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

    private Integer trainSamples;

    private Integer valSamples;

    private Double trainLoss;

    private Double valLoss;

    private LocalDateTime createdAt;
}
// AI_GENERATE_END -----
