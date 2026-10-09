// AI_GENERATE_START -
package com.stock.modelService.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Python 候选 ONNX 制品的兼容元数据。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Data
public class OnnxModelMetadata {

    /** 模型名称。 */
    @JsonProperty("model_name")
    private String modelName;

    /** 模型版本。 */
    @JsonProperty("model_version")
    private String modelVersion;

    /** 制品格式，必须为 onnx。 */
    @JsonProperty("artifact_format")
    private String artifactFormat;

    /** 模型精度。 */
    private String precision;

    /** ONNX Opset。 */
    @JsonProperty("opset_version")
    private int opsetVersion;

    /** Java 特征版本。 */
    @JsonProperty("feature_version")
    private String featureVersion;

    /** 标签版本。 */
    @JsonProperty("label_version")
    private String labelVersion;

    /** ONNX 输入名。 */
    @JsonProperty("input_names")
    private List<String> inputNames;

    /** ONNX 输出名。 */
    @JsonProperty("output_names")
    private List<String> outputNames;

    /** model.onnx 的 SHA-256。 */
    @JsonProperty("model_sha256")
    private String modelSha256;

    /** Java 加载前必须检查的结构参数。 */
    private Map<String, Object> compatibility;
}
// AI_GENERATE_END -
