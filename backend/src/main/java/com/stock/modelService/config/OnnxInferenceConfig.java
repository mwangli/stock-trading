// AI_GENERATE_START -
package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ONNX Runtime 候选制品和影子推理配置。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.onnx")
public class OnnxInferenceConfig {

    /** 是否允许加载 ONNX 候选制品。 */
    private boolean enabled;

    /** 是否只做影子推理而不替换 DJL 主结果。 */
    private boolean shadowEnabled = true;

    /** LSTM 候选制品目录。 */
    private String lstmArtifactDir = "./models/onnx/lstm";

    /** 情感候选制品目录。 */
    private String sentimentArtifactDir = "./models/onnx/sentiment";

    /** ONNX 单算子线程数。 */
    private int intraOpThreads = 1;

    /** ONNX 算子间线程数。 */
    private int interOpThreads = 1;

    /** 影子输出允许的最大绝对误差。 */
    private double outputTolerance = 0.0001D;
}
// AI_GENERATE_END -
