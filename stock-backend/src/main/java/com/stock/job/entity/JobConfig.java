// AI_GENERATE_START-------
package com.stock.job.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import java.time.LocalDateTime;

/**
 * MongoDB 固定任务配置文档。
 * 保存任务名称、Cron、启停状态和任务分类，供应用启动时重建 Quartz RAMJobStore。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Document(collection = "job_config")
@Accessors(chain = true)
public class JobConfig {

    @Id
    private Long id;

    @Indexed(unique = true, name = "uk_job_config_name")
    private String jobName;

    private String cronExpression;

    private Integer status;

    private String description;

    /** 任务所属业务模块。 */
    private String module;

    /** 同步模式或任务类型，支持 INCREMENTAL、BACKFILL、FULL。 */
    private String syncMode;

    @CreatedDate
    private LocalDateTime createTime;

    @LastModifiedDate
    private LocalDateTime updateTime;
}
// AI_GENERATE_END-------
