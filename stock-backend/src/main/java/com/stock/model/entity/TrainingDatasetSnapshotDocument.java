// AI_GENERATE_START-----------
package com.stock.model.entity;

import lombok.Data;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 已完成特征计算和样本切分的 LSTM 训练数据快照元数据。
 * 快照工件独立于模型版本保存，允许多个训练任务复用同一份训练集和验证集。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Entity
@Table(name = "training_dataset_snapshot", indexes = {
        @Index(name = "idx_snapshot_model_created", columnList = "model_name, created_at")
})
public class TrainingDatasetSnapshotDocument {

    /** 快照 ID。 */
    @Id
    @GeneratedValue
    @UuidGenerator
    private String id;

    /** 模型逻辑名称。 */
    private String modelName;

    /** 快照状态，如 BUILDING、READY、FAILED、EXPIRED。 */
    private String status;

    /** 数据契约摘要，用于判断是否可以复用。 */
    private String contractHash;

    /** 特征定义版本。 */
    private String featureVersion;

    /** 数据截止日期。 */
    private LocalDate datasetEndDate;

    /** 每只股票读取的交易日数量。 */
    private int days;

    /** 快照包含的股票数量。 */
    private int stockCount;

    /** 有效训练股票数量。 */
    private int trainedStocks;

    /** 训练样本数量。 */
    private int trainSamples;

    /** 验证样本数量。 */
    private int valSamples;

    /** 独立测试样本数量。 */
    private int testSamples;

    /** 主策略 Walk-Forward 折叠数量。 */
    private int walkForwardFoldCount;

    /** 60 日严格隔离敏感性折叠数量。 */
    private int strictIsolationFoldCount;

    /** 全市场日期切分策略版本。 */
    private String splitPolicyVersion;

    /** 模型数据来源版本。 */
    private String dataSourceVersion;

    /** 训练集开始日期。 */
    private LocalDate trainStartDate;

    /** 训练集结束日期。 */
    private LocalDate trainEndDate;

    /** 验证集开始日期。 */
    private LocalDate validationStartDate;

    /** 验证集结束日期。 */
    private LocalDate validationEndDate;

    /** 独立测试集开始日期。 */
    private LocalDate testStartDate;

    /** 独立测试集结束日期。 */
    private LocalDate testEndDate;

    /** 工件存储类型，如 MONGO_GRIDFS 或 LOCAL_FILE。 */
    private String artifactStorage;

    /** MongoDB GridFS 中的清单或单文件工件 ID。 */
    private String artifactFileId;

    /** 快照文件 SHA-256。 */
    private String artifactSha256;

    /** 快照文件大小，单位字节。 */
    private long artifactSize;

    /** 快照制品格式，如 CHUNKED_JSON_GZIP_V1。 */
    private String artifactFormat;

    /** 快照分片数量，历史单文件快照为 0。 */
    private int chunkCount;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 完成时间。 */
    private LocalDateTime finishedAt;

    /** 失败原因。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;
}
// AI_GENERATE_END-----------
