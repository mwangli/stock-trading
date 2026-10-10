// AI_GENERATE_START -
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

    private String errorMessage;

    private Integer trainSamples;

    private Integer valSamples;

    private Double trainLoss;

    private Double valLoss;

    private LocalDateTime createdAt;
}
// AI_GENERATE_END -
