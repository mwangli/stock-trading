// AI_GENERATE_START---- ---
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统运行控制配置。
 * 保存调度和应用启动任务等可动态调整的非敏感开关。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Data
@Entity
@Table(name = "system_runtime_config")
public class SystemRuntimeConfig {

    /** 节点标识，同一数据库中的每个运行节点必须保持唯一。 */
    @Id
    @Column(name = "node_id", nullable = false, length = 128)
    private String nodeId;

    /** 是否允许 Quartz 固定任务执行。 */
    @Column(name = "scheduler_enabled", nullable = false)
    private boolean schedulerEnabled;

    /** 兼容旧表结构的历史字段，非交易任务不再读取该值。 */
    @Column(name = "data_collection_enabled", nullable = false)
    private boolean dataCollectionEnabled;

    /** 兼容旧表结构的历史字段，非交易任务不再读取该值。 */
    @Column(name = "news_collection_enabled", nullable = false)
    private boolean newsCollectionEnabled;

    /** 是否在应用启动后执行股票基础数据初始化。 */
    @Column(name = "startup_data_sync_enabled", nullable = false)
    private boolean startupDataSyncEnabled;

    /** 兼容旧表结构的历史字段，模型训练不再读取该值。 */
    @Column(name = "model_training_enabled", nullable = false)
    private boolean modelTrainingEnabled;

    /** 配置最后更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 初始化配置更新时间。 */
    @PrePersist
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    /** 刷新配置更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END---- ----
