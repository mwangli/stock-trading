package com.stock.modelService.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 训练响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingResponse {

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