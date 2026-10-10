// AI_GENERATE_START -
package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * LSTM 不可变模型版本文档。
 * MongoDB 保存小型模型参数、训练配置、输入契约、指标和可审计状态。
 * 当前激活指针由独立的 {@link ModelActivationDocument} 维护。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Document(collection = "lstm_models")
@CompoundIndex(name = "idx_model_created", def = "{'modelName': 1, 'createdAt': -1}")
public class LstmModelDocument {

    @Id
    private String id;
    private String modelName;
    private String modelVersion;
    private String parentModelVersionId;
    private String trainingConfigJson;
    private String inputContractJson;
    private String metricsJson;
    private String featureVersion;
    private String labelVersion;
    private String engineName;
    private String engineVersion;
    private String djlVersion;
    private String parameterSha256;
    private long parameterSize;
    private String status;
    private int epoch;
    @Indexed(name = "idx_createdAt", useGeneratedName = false)
    private LocalDateTime createdAt;
    private byte[] params;
    private String normalizationParams;
    private Double trainLoss;
    private Double valLoss;
}
// AI_GENERATE_END -
