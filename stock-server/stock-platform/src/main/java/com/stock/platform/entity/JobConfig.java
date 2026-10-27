// AI_GENERATE_START----------
package com.stock.platform.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;

/**
 * MySQL 固定任务配置实体。
 * 保存任务名称、Cron、启停状态和任务分类，供应用启动时重建 Quartz RAMJobStore。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Entity
@Table(name = "job_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_config_name", columnNames = "job_name")
})
@Accessors(chain = true)
public class JobConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String jobName;

    private String cronExpression;

    private Integer status;

    private String description;

    /** 任务所属业务模块。 */
    private String module;

    /** 同步模式或任务类型，支持 INCREMENTAL、BACKFILL、FULL。 */
    private String syncMode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 初始化任务配置时间。 */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createTime = createTime == null ? now : createTime;
        updateTime = now;
    }

    /** 刷新任务配置更新时间。 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
// AI_GENERATE_END----------
