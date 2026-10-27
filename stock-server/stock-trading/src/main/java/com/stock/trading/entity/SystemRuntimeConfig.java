// AI_GENERATE_START------------------
package com.stock.trading.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统运行控制配置。
 * 保存应用启动任务和自动交易紧急停止等运行控制配置。
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
    private String nodeId;

    /** 是否在应用启动后执行股票基础数据初始化。 */
    private boolean startupDataSyncEnabled;

    /** 是否已通过紧急停止阻止后续自动交易。 */
    private boolean automaticTradingPaused;

    /** 最近一次紧急停止原因。 */
    private String emergencyStopReason;

    /** 最近一次紧急停止时间。 */
    private LocalDateTime emergencyStoppedAt;

    /** 最近一次紧急停止操作者。 */
    private String emergencyStopOperator;

    /** 紧急停止时生效的策略版本。 */
    private String emergencyStopStrategyVersion;

    /** 最近一次紧急停止执行结果。 */
    private String emergencyStopResult;

    /** 配置最后更新时间。 */
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
// AI_GENERATE_END------------------
