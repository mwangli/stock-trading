// AI_GENERATE_START---
package com.stock.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 情感模型制品清单。
 * 固化不可变模型版本、文件摘要、输入契约和离线评估摘要。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SentimentArtifactManifest {

    /** 清单契约版本。 */
    private String schemaVersion;

    /** 模型逻辑名称。 */
    private String modelName;

    /** 不可变模型版本。 */
    private String modelVersion;

    /** 训练或微调使用的基础模型。 */
    private String baseModel;

    /** 推理引擎名称。 */
    private String engineName;

    /** 最大输入序列长度。 */
    private Integer maxSequenceLength;

    /** 模型输出标签顺序。 */
    private List<String> labels;

    /** 制品文件列表。 */
    private List<ArtifactFile> files;

    /** 离线评估摘要。 */
    private EvaluationMetrics metrics;

    /**
     * 制品文件描述。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArtifactFile {

        /** 相对于版本目录的文件路径。 */
        private String path;

        /** 文件字节数。 */
        private Long size;

        /** 文件 SHA-256 摘要。 */
        private String sha256;
    }

    /**
     * 离线评估指标摘要。
     *
     * @author mwangli
     * @since 2026-10-12
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvaluationMetrics {

        /** 人工复核评估集版本。 */
        private String datasetVersion;

        /** 评估样本数量。 */
        private Integer sampleCount;

        /** 三分类 Macro-F1。 */
        private Double macroF1;

        /** 负面类别召回率。 */
        private Double negativeRecall;

        /** 期望校准误差 ECE。 */
        private Double expectedCalibrationError;

        /** 单条推理 P95 耗时，单位毫秒。 */
        private Double p95LatencyMs;

        /** 评估结论，如 PASSED、REJECTED 或 OBSERVE。 */
        private String conclusion;
    }
}
// AI_GENERATE_END---
