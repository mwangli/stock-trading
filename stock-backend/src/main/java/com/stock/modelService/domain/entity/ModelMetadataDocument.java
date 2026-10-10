// AI_GENERATE_START -
package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 外部大模型制品元数据文档。
 * 大体积权重保留在模型目录，MongoDB只保存配置、指标、状态、摘要和路径。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Document(collection = "model_metadata")
public class ModelMetadataDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String modelName;

    private String modelVersion;

    private String artifactUri;

    private String configJson;

    private String metricsJson;

    private String status;

    private String engineName;

    private String lastError;

    private LocalDateTime updatedAt;
}
// AI_GENERATE_END -
