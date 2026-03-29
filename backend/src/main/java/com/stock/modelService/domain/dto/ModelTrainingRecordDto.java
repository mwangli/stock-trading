package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型训练记录列表项 DTO
 * 用于前端展示“量化模型”列表，对应 MySQL 中的模型训练记录，
 * 同时兼容原有 LSTM 模型列表字段，便于前端无感迁移。
 *
 * <p>其中：</p>
 * <ul>
 *     <li>{@code modelName} 使用股票代码</li>
 *     <li>{@code name} 使用股票名称</li>
 *     <li>{@code createdAt} 使用最近一次训练时间（未训练则为记录创建时间）</li>
 *     <li>{@code trained} 表示是否已有对应 MongoDB 模型</li>
 * </ul>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTrainingRecordDto {

    /**
     * 训练记录主键 ID
     */
    private Long id;

    /**
     * 股票代码（同时作为前端的 modelName）
     */
    private String stockCode;

    /**
     * 股票名称（同时作为前端的 name）
     */
    private String stockName;

    /**
     * 是否已训练（Mongo 中存在对应模型）
     */
    private boolean trained;

    /**
     * 是否处于训练中状态
     */
    private boolean training;

    /**
     * 最近一次训练完成时间
     */
    private LocalDateTime lastTrainTime;

    /**
     * 最近一次训练耗时（秒）
     */
    private Long lastDurationSeconds;

    /**
     * 最近一次训练轮次
     */
    private Integer lastEpochs;

    /**
     * 最近一次训练集损失
     */
    private Double lastTrainLoss;

    /**
     * 最近一次验证集损失
     */
    private Double lastValLoss;

    /**
     * 最近一次训练对应的 Mongo 模型 ID
     */
    private String lastModelId;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 记录最近更新时间
     */
    private LocalDateTime updatedAt;

    // 兼容前端原有 LstmModelListItem 字段映射
    private String modelName;
    private String name;
    private int epoch;
    private String modelVersion;
    private Double profitAmount;
    private Double score;
    private Double price;
    private Double changePercent;
}

