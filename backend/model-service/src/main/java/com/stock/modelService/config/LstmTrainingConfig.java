package com.stock.modelService.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * LSTM 模型训练配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "models.lstm")
public class LstmTrainingConfig {

    /**
     * 模型保存路径
     */
    private String modelPath = "models/lstm-stock";

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
    private int inputSize = 5;

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
