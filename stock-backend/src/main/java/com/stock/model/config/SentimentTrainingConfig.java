// AI_GENERATE_START--------------
package com.stock.model.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DJL PyTorch 情感模型配置。
 * 大体积权重使用版本化外部目录，应用只保存版本引用和校验约束。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.sentiment")
public class SentimentTrainingConfig {

    /** 模型逻辑名称。 */
    private String modelName = "sentiment-finbert";

    /** 不可变制品版本。 */
    private String modelVersion = "unconfigured";

    /** 远程或制品清单标识的基础模型。 */
    private String pretrainedModel = "yiyanghkust/finbert-tone-chinese";

    /** 固定的本地模型根目录，直接指向已发布的模型制品目录。 */
    private String modelPath = "./model-files/sentiment";

    /** 最大输入序列长度。 */
    private int maxSequenceLength = 128;

    /** 金标验证集拟合得到的温度缩放参数，未校准时固定为 1。 */
    private double calibrationTemperature = 1D;

    /** 预留训练轮次，仅用于后续真实训练流水线。 */
    private int epochs = 5;

    /** 预留训练批次大小，仅用于后续真实训练流水线。 */
    private int batchSize = 16;

    /** 预留训练学习率，仅用于后续真实训练流水线。 */
    private double learningRate = 2e-5;

    /** 预留训练集比例，仅用于后续真实训练流水线。 */
    private double trainRatio = 0.8;

    /** 情感分类标签数量。 */
    private int numLabels = 3;

    /** 单个模型文件最大字节数。 */
    private long maxSingleFileBytes = 2L * 1024 * 1024 * 1024;

    /** 单个模型版本总字节数上限。 */
    private long maxTotalArtifactBytes = 4L * 1024 * 1024 * 1024;

    /** 情感标签顺序，必须与 manifest 和模型输出一致。 */
    private String[] labels = {"neutral", "positive", "negative"};
}
// AI_GENERATE_END--------------
