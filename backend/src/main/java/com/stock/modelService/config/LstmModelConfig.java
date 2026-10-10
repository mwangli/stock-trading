// AI_GENERATE_START --
package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** LSTM 特征契约、面板切分和 DJL 模型输入参数。 */
@Data
@Component
@ConfigurationProperties(prefix = "models.lstm")
public class LstmModelConfig {

    private int sequenceLength = 60;
    private int inputSize = 16;
    private double trainRatio = 0.8D;
    private int validationGap = 2;
    private double targetReturnScale = 0.1D;
    private int maxPanelSamples = 100000;
    private int maxSamplesPerStock = 80;
    private int stockEmbeddingBuckets = 8192;
    private int stockEmbeddingSize = 16;
    private int industryEmbeddingBuckets = 512;
    private int industryEmbeddingSize = 8;
    private int groupHeadCount = 8;
    private boolean residualHeadEnabled = true;
    private double groupHeadScale = 0.1D;
    private double residualHeadScale = 0.1D;
}
// AI_GENERATE_END --
