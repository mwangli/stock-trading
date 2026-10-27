// AI_GENERATE_START-------------
package com.stock.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.stock.platform.enums.JobExecutionStatus;
import com.stock.platform.enums.JobTriggerSource;

import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 任务单次执行记录。
 * 独立保存定时和手动触发的运行状态，避免覆盖任务定义中的最近一次结果。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Entity
@Table(name = "job_execution_record", indexes = {
        @Index(name = "idx_job_execution_name_created", columnList = "job_name, created_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_job_execution_run", columnNames = "run_id")
})
@Accessors(chain = true)
public class JobExecutionRecord {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外查询使用的唯一运行标识。 */
    private String runId;

    /** 固定任务名称。 */
    private String jobName;

    /** 任务触发来源。 */
    @Enumerated(EnumType.STRING)
    private JobTriggerSource triggerSource;

    /** 触发操作人或系统标识。 */
    private String triggeredBy;

    /** 任务运行状态。 */
    @Enumerated(EnumType.STRING)
    private JobExecutionStatus status;

    /** Quartz 计划触发时间。 */
    private LocalDateTime scheduledTime;

    /** 实际开始时间。 */
    private LocalDateTime startedTime;

    /** 实际结束时间。 */
    private LocalDateTime finishedTime;

    /** 执行耗时，单位为毫秒。 */
    private Long durationMs;

    /** 脱敏后的异常类型。 */
    private String errorType;

    /** 脱敏后的错误摘要。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    /** 脱敏后的执行结果摘要。 */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultSummary;

    /** 执行节点名称。 */
    private String hostName;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;

    /** 初始化执行记录创建时间。 */
    @PrePersist
    protected void onCreate() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
// AI_GENERATE_END-------------
