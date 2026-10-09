// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Java 与 Python 共享的不可变训练数据快照清单。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Data
@Builder
@JsonPropertyOrder({
        "dataset_id", "dataset_type", "feature_version", "label_version",
        "created_at", "data_cutoff", "schema_version", "files", "metadata"
})
public class TrainingSnapshotManifest {

    /** 快照唯一标识。 */
    @JsonProperty("dataset_id")
    private String datasetId;

    /** 数据集类型：lstm-panel 或 sentiment-text。 */
    @JsonProperty("dataset_type")
    private String datasetType;

    /** 特征定义版本。 */
    @JsonProperty("feature_version")
    private String featureVersion;

    /** 标签定义版本。 */
    @JsonProperty("label_version")
    private String labelVersion;

    /** 快照创建时间，使用带时区的 ISO-8601。 */
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    /** 快照包含数据的最晚业务时间。 */
    @JsonProperty("data_cutoff")
    private OffsetDateTime dataCutoff;

    /** Schema 契约版本。 */
    @JsonProperty("schema_version")
    private String schemaVersion;

    /** 快照内所有受摘要保护的文件。 */
    private List<FileEntry> files;

    /** 数据集专用元数据。 */
    private Map<String, Object> metadata;

    /**
     * 快照文件摘要条目。
     *
     * @author mwangli
     * @since 2026-10-09
     */
    @Data
    @Builder
    @JsonPropertyOrder({"path", "sha256", "size_bytes", "content_type", "split", "record_count"})
    public static class FileEntry {

        /** 相对快照根目录的文件路径。 */
        private String path;

        /** 文件 SHA-256，小写十六进制。 */
        private String sha256;

        /** 文件字节数。 */
        @JsonProperty("size_bytes")
        private long sizeBytes;

        /** 文件内容类型。 */
        @JsonProperty("content_type")
        private String contentType;

        /** 文件角色：schema、train、validation 或 test。 */
        private String split;

        /** JSONL 数据记录数；Schema 文件固定为 0。 */
        @JsonProperty("record_count")
        private long recordCount;
    }

    /**
     * JSONL 数据文件的有序字段 Schema。
     *
     * @author mwangli
     * @since 2026-10-09
     */
    @Data
    @Builder
    @JsonPropertyOrder({"schema_version", "dataset_type", "columns"})
    public static class DatasetSchema {

        /** Schema 契约版本。 */
        @JsonProperty("schema_version")
        private String schemaVersion;

        /** 数据集类型。 */
        @JsonProperty("dataset_type")
        private String datasetType;

        /** JSONL 字段定义，列表顺序即文件中的字段顺序。 */
        private List<ColumnDefinition> columns;
    }

    /**
     * 单个 JSONL 字段定义。
     *
     * @author mwangli
     * @since 2026-10-09
     */
    @Data
    @Builder
    @JsonPropertyOrder({"name", "dtype", "nullable", "shape"})
    public static class ColumnDefinition {

        /** 字段名。 */
        private String name;

        /** 跨语言数据类型。 */
        private String dtype;

        /** 是否允许 null。 */
        private boolean nullable;

        /** 矩阵固定维度；标量字段为 null。 */
        private List<Integer> shape;
    }
}
// AI_GENERATE_END -
