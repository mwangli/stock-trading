// AI_GENERATE_START --
package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ONNX Runtime 在线推理配置。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.onnx")
public class OnnxInferenceConfig {

    /** 是否允许加载 ONNX 制品。 */
    private boolean enabled = true;

    /** LSTM 候选制品目录。 */
    private String lstmArtifactDir = "./models/onnx/lstm";

    /** 情感候选制品目录。 */
    private String sentimentArtifactDir = "./models/onnx/sentiment";

    /** ONNX 单算子线程数。 */
    private int intraOpThreads = 1;

    /** ONNX 算子间线程数。 */
    private int interOpThreads = 1;

}
// AI_GENERATE_END --
