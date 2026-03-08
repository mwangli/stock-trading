package com.stock.modelService.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;

import java.time.LocalDateTime;

/**
 * LSTM 模型二进制存储文档
 * 用于在 MongoDB 中保存训练好的模型参数和归一化配置
 */
@Data
@Document(collection = "lstm_models")
@CompoundIndex(name = "idx_model_created", def = "{'modelName': 1, 'createdAt': -1}")
public class LstmModelDocument {

    @Id
    private String id;

    /**
     * 模型名称（与 DJL 保存时的 modelName 一致）
     */
    private String modelName;

    /**
     * 训练所在 epoch
     */
    private int epoch;

    /**
     * 保存时间
     */
    private LocalDateTime createdAt;

    /**
     * 模型参数二进制（对应 *.params 文件）
     */
    private byte[] params;

    /**
     * 归一化等训练配置（normalization_params_xxx.txt 的内容）
     */
    private String normalizationParams;
    /**
     * Model version (e.g., "v1") to handle schema evolution.
     */
    private String modelVersion;
}

