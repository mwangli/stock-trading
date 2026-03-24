package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LSTM 模型训练配置
 * 负责控制模型训练的超参数与模型持久化方式（本地文件 / MongoDB）
 *
 * @author mwangli
 * @since 2026-03-13
 */
@Data
@Component
@ConfigurationProperties(prefix = "djl.model.lstm")
public class LstmTrainingConfig {

    /**
     * 模型存储类型
     * 可选值：
     * - mongo：以二进制形式持久化到 MongoDB（默认）
     * - local：以文件形式持久化到本地目录
     */
    private String storageType = "mongo";

    /**
     * 本地文件系统模型根目录（容器内部路径）
     * 当 storageType=local 时，模型文件将保存到该目录下
     * 默认挂载到 /models/lstm，对应 docker-compose 中的 models-data 卷
     */
    private String localBasePath = "/models/lstm";

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
     * 特征维度（开、高、低、收、量）
     */
    private int inputSize = 11;

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
