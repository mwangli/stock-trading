// AI_GENERATE_START-----
package com.stock.model.entity;

import lombok.Data;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 外部大模型制品元数据实体。
 * 大体积权重保留在模型目录或 GridFS，MySQL 只保存配置、指标、状态、摘要和路径。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Entity
@Table(name = "model_metadata", uniqueConstraints = {
        @UniqueConstraint(name = "uk_model_metadata_name", columnNames = "model_name")
})
public class ModelMetadataDocument {

    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    private String modelName;

    private String modelVersion;

    @Lob
    private String artifactUri;

    /** 模型制品存储方式，例如 MONGO_GRIDFS。 */
    private String artifactStorage;

    /** GridFS 文件 ID 列表 JSON。 */
    @Lob
    private String artifactFileIdsJson;

    @Lob
    private String configJson;

    @Lob
    private String metricsJson;

    private String status;

    private String engineName;

    @Lob
    private String lastError;

    private LocalDateTime updatedAt;
}
// AI_GENERATE_END-----
