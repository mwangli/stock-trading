// AI_GENERATE_START -----
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

    /** 远程或制品清单标识的基础模型。 */
    private String pretrainedModel = "yiyanghkust/finbert-tone-chinese";

    /** 固定的本地模型根目录，目录由初始化流程维护。 */
    private String modelPath = "data/models/sentiment";

    /** 模型托管平台固定 revision。 */
    private String pretrainedRevision = "main";

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

    /** 是否允许首次启动从模型托管平台下载并初始化本地目录。 */
    private boolean downloadPretrained = true;

    /** 是否在初始化阶段调用 DJL Converter 生成 TorchScript 权重。 */
    private boolean converterEnabled = true;

    /** DJL Converter 可执行命令。 */
    private String converterCommand = "djl-convert";

    /** DJL Converter 输出格式。 */
    private String converterOutputFormat = "TorchScript";

    /** DJL Converter 单次执行超时时间，单位秒。 */
    private long converterTimeoutSeconds = 1800L;

    /** 单个模型文件最大字节数。 */
    private long maxSingleFileBytes = 2L * 1024 * 1024 * 1024;

    /** 单个模型版本总字节数上限。 */
    private long maxTotalArtifactBytes = 4L * 1024 * 1024 * 1024;

    /** 情感标签顺序，必须与 manifest 和模型输出一致。 */
    private String[] labels = {"neutral", "positive", "negative"};
}
// AI_GENERATE_END -----
