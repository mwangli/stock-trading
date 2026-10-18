// AI_GENERATE_START ---
package com.stock.tradingExecutor.runtime.domain.entity;

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
 * 保存调度、数据采集和模型训练等可动态调整的非敏感开关。
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

    /** 是否允许股票列表、行情和历史数据采集。 */
    @Column(name = "data_collection_enabled", nullable = false)
    private boolean dataCollectionEnabled;

    /** 是否允许新闻和公告采集。 */
    @Column(name = "news_collection_enabled", nullable = false)
    private boolean newsCollectionEnabled;

    /** 是否在应用启动后执行股票基础数据初始化。 */
    @Column(name = "startup_data_sync_enabled", nullable = false)
    private boolean startupDataSyncEnabled;

    /** 是否在应用启动后执行历史行情同步。 */
    @Column(name = "startup_history_sync_enabled", nullable = false)
    private boolean startupHistorySyncEnabled;

    /** 是否在应用启动后执行新闻采集。 */
    @Column(name = "startup_news_sync_enabled", nullable = false)
    private boolean startupNewsSyncEnabled;

    /** 是否允许提交 LSTM 模型训练任务。 */
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
// AI_GENERATE_END ---
