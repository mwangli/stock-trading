package com.stock.modelService.domain.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 情感模型版本实体
 * 用于在 MongoDB 中记录情感分析模型的版本信息，支持版本追溯和部署管理。
 *
 * <p>每条记录对应一个模型版本，包含版本号、描述、模型路径、性能指标、
 * 状态以及部署/弃用时间等信息。</p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Data
@Document(collection = "sentiment_model_versions")
public class SentimentModelVersion {

    /**
     * MongoDB 文档 ID
     */
    @Id
    private String id;

    /**
     * 版本号，格式如 v1.0.0
     */
    @Indexed(name = "idx_version", unique = true)
    private String version;

    /**
     * 版本描述，说明该版本的主要变更内容
     */
    private String description;

    /**
     * 模型存储路径，指向 MongoDB 中模型二进制文件的存储位置
     */
    private String modelPath;

    /**
     * 父版本号，用于版本追溯
     */
    @Indexed(name = "idx_parent_version")
    private String parentVersion;

    /**
     * 模型准确率
     */
    private Double accuracy;

    /**
     * 模型 F1 分数
     */
    private Double f1Score;

    /**
     * 模型夏普比率，用于评估模型风险调整后的收益表现
     */
    private Double sharpeRatio;

    /**
     * 训练样本数量
     */
    private Integer trainingSamples;

    /**
     * 版本状态：active-活跃版本、deprecated-已弃用、rollback-已回滚
     */
    @Indexed(name = "idx_status")
    private String status;

    /**
     * 部署时间
     */
    @Indexed(name = "idx_deployed_at")
    private LocalDateTime deployedAt;

    /**
     * 弃用时间
     */
    private LocalDateTime deprecatedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}