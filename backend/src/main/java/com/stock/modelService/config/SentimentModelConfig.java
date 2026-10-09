// AI_GENERATE_START -
package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 情感数据快照与 ONNX 推理契约配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "models.sentiment")
public class SentimentModelConfig {

    private double trainRatio = 0.8D;
    private int maxSequenceLength = 128;
    private String[] labels = {"neutral", "positive", "negative"};
}
// AI_GENERATE_END -
