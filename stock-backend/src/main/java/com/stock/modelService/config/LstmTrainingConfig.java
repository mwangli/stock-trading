// AI_GENERATE_START ----
package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LSTM 模型结构和训练配置。
 * 配置用于创建新版本；发布时会将完整训练配置固化到 MongoDB 版本文档。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.lstm")
public class LstmTrainingConfig {

    /** 模型逻辑名称。 */
    private String modelName = "global-shared-lstm";

    /**
     * 是否允许当前节点执行模型训练，在线推理节点应保持关闭。
     */
    private boolean trainingEnabled = false;

    /**
     * 输入序列长度（时间步）
     */
    private int sequenceLength = 60;

    /**
     * 隐藏层大小
     */
    private int hiddenSize = 50;

    /**
     * LSTM 层数
     */
    private int numLayers = 2;

    /**
     * 训练轮次
     */
    private int epochs = 100;

    /**
     * 批次大小
     */
    private int batchSize = 32;

    /**
     * 学习率
     */
    private double learningRate = 0.001;

    /**
     * Dropout 率
     */
    private double dropout = 0.2;

    /**
     * 训练集比例
     */
    private double trainRatio = 0.8;

    /**
     * 训练集与验证集之间隔离的样本数，避免重叠窗口造成近邻泄漏。
     */
    private int validationGap = 2;

    /**
     * 下一交易日收益率标签缩放值，0.1 表示百分之十收益映射为 1。
     */
    private double targetReturnScale = 0.1D;

    /**
     * 单次共享训练允许保留的最大面板样本数量。
     */
    private int maxPanelSamples = 100000;

    /**
     * 单只股票允许保留的最大均匀时间窗口数量。
     */
    private int maxSamplesPerStock = 80;

    /**
     * 每个时间步的总特征维度。
     */
    private int inputSize = 16;

    /**
     * 股票 Embedding 哈希桶数量，索引 0 保留给未知股票。
     */
    private int stockEmbeddingBuckets = 8192;

    /**
     * 股票 Embedding 维度。
     */
    private int stockEmbeddingSize = 16;

    /**
     * 行业 Embedding 哈希桶数量，索引 0 保留给未知行业。
     */
    private int industryEmbeddingBuckets = 512;

    /**
     * 行业 Embedding 维度。
     */
    private int industryEmbeddingSize = 8;

    /**
     * 行业或风格分组 Head 数量。
     */
    private int groupHeadCount = 8;

    /**
     * 是否启用个股 Residual Head。
     */
    private boolean residualHeadEnabled = true;

    /**
     * 分组 Head 对共享输出的校正缩放系数。
     */
    private double groupHeadScale = 0.1D;

    /**
     * 个股 Residual Head 对共享输出的校正缩放系数。
     */
    private double residualHeadScale = 0.1D;

    /**
     * 收益率任务损失权重。
     */
    private double returnLossWeight = 0.5D;

    /**
     * 方向概率任务损失权重。
     */
    private double directionLossWeight = 0.3D;

    /**
     * 下行风险任务损失权重。
     */
    private double downsideLossWeight = 0.2D;

    /**
     * 是否启用早停
     */
    private boolean earlyStopping = true;

    /**
     * 早停耐心值
     */
    private int patience = 10;

    /**
     * 最小 delta（早停判断）
     */
    private double minDelta = 0.0001;
}
// AI_GENERATE_END ----
