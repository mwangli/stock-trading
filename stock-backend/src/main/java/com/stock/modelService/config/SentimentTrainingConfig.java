// AI_GENERATE_START ---
package com.stock.modelService.config;

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

    /** 情感模型版本根目录。 */
    private String modelPath = "data/models/sentiment";

    /** 模型加载来源：local 或 huggingface。 */
    private String modelSource = "local";

    /** 远程或制品清单标识的基础模型。 */
    private String pretrainedModel = "yiyanghkust/finbert-tone-chinese";

    /** 最大输入序列长度。 */
    private int maxSequenceLength = 128;

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

    /** 是否允许 DJL 在线下载预训练模型。生产环境必须关闭。 */
    private boolean downloadPretrained = false;

    /** DJL 模型缓存目录。 */
    private String cacheDir = "data/models/cache";

    /** 是否允许没有 manifest 的历史平铺目录。 */
    private boolean allowLegacyLayout = false;

    /** 单个模型文件最大字节数。 */
    private long maxSingleFileBytes = 2L * 1024 * 1024 * 1024;

    /** 单个模型版本总字节数上限。 */
    private long maxTotalArtifactBytes = 4L * 1024 * 1024 * 1024;

    /** 情感标签顺序，必须与 manifest 和模型输出一致。 */
    private String[] labels = {"neutral", "positive", "negative"};
}
// AI_GENERATE_END ---
