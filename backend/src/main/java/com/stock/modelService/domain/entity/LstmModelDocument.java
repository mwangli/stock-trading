package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private String modelName;
    private int epoch;
    @Indexed(name = "idx_createdAt", useGeneratedName = false)
    private LocalDateTime createdAt;
    private byte[] params;
    private String normalizationParams;
    private String modelVersion;
    private Double trainLoss;
    private Double valLoss;
}
