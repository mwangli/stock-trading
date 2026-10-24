// AI_GENERATE_START----------
package com.stock.model.entity;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 模型训练运行记录。
 * 保存训练触发、门禁、执行状态和候选模型版本，供运维页面查询与审计。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Entity
@Table(name = "model_training_run", indexes = {
        @Index(name = "idx_training_model_created", columnList = "model_name, created_at")
})
public class ModelTrainingRunDocument {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String modelName;

    private String status;

    private String triggerSource;

    private String triggeredBy;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String requestedConfigJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String effectiveConfigJson;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String gateResultJson;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String candidateModelVersionId;

    /** 本次训练使用的训练数据快照 ID。 */
    private String datasetSnapshotId;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    /** 实际参与训练并产生有效样本的股票数量。 */
    private Integer trainedStocks;

    /** 当前训练阶段。 */
    private String progressStage;

    /** 当前训练阶段说明。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
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
// AI_GENERATE_END----------
