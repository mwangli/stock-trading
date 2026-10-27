// AI_GENERATE_START---------
package com.stock.model.entity;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * LSTM 不可变模型版本实体。
 * MySQL 保存模型元数据、训练配置、输入契约、指标和可审计状态。
 * 当前激活指针由独立的 {@link ModelActivationDocument} 维护。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Entity
@Table(name = "lstm_model", indexes = {
        @Index(name = "idx_model_created", columnList = "model_name, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_lstm_model_version", columnNames = {"model_name", "model_version"})
})
public class LstmModelDocument {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;
    private String modelName;
    private String modelVersion;
    private String parentModelVersionId;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String trainingConfigJson;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String inputContractJson;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
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
    private LocalDateTime createdAt;
    /** GridFS 中的模型参数文件 ID。 */
    private String parameterFileId;
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String normalizationParams;
    private Double trainLoss;
    private Double valLoss;
}
// AI_GENERATE_END---------
