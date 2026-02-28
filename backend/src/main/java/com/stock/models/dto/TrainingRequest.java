package com.stock.models.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * LSTM 训练请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingRequest {

    /**
     * 股票代码（逗号分隔，多个股票联合训练）
     */
    private String stockCodes;

    /**
     * 训练数据天数
     */
    @Builder.Default
    private Integer days = 365;

    /**
     * 训练轮次（可选，覆盖默认配置）
     */
    private Integer epochs;

    /**
     * 批次大小（可选）
     */
    private Integer batchSize;

    /**
     * 学习率（可选）
     */
    private Double learningRate;
}

/**
 * 训练响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TrainingResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 训练轮次
     */
    private Integer epochs;

    /**
     * 最终训练损失
     */
    private Double trainLoss;

    /**
     * 验证损失
     */
    private Double valLoss;

    /**
     * 模型保存路径
     */
    private String modelPath;

    /**
     * 训练样本数
     */
    private Integer trainSamples;

    /**
     * 验证样本数
     */
    private Integer valSamples;

    /**
     * 训练详情（每轮损失）
     */
    private List<Map<String, Object>> details;
}

/**
 * 训练进度
 */
@Data
@Builder
class TrainingProgress {

    /**
     * 当前轮次
     */
    private int currentEpoch;

    /**
     * 总轮次
     */
    private int totalEpochs;

    /**
     * 当前损失
     */
    private double loss;

    /**
     * 进度百分比
     */
    private double progress;

    /**
     * 状态信息
     */
    private String status;
}
