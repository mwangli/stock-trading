// AI_GENERATE_START------------
package com.stock.trading.entity;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

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
@Document(collection = "system_runtime_config")
public class SystemRuntimeConfig {

    /** 节点标识，同一数据库中的每个运行节点必须保持唯一。 */
    @Id
    private String nodeId;

    /** 是否允许 Quartz 固定任务执行。 */
    private boolean schedulerEnabled;


    /** 是否在应用启动后执行股票基础数据初始化。 */
    private boolean startupDataSyncEnabled;


    /** 配置最后更新时间。 */
    private LocalDateTime updatedAt;

    /** 初始化配置更新时间。 */
    protected void onCreate() {
        updatedAt = LocalDateTime.now();
    }

    /** 刷新配置更新时间。 */
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
// AI_GENERATE_END------------
