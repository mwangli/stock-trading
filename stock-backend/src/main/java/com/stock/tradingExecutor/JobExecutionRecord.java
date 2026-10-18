// AI_GENERATE_START- --
package com.stock.tradingExecutor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
        @Index(name = "idx_job_execution_job_time", columnList = "job_name,created_at"),
        @Index(name = "idx_job_execution_status", columnList = "status"),
        @Index(name = "idx_job_execution_run_id", columnList = "run_id", unique = true)
})
@Accessors(chain = true)
public class JobExecutionRecord {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 对外查询使用的唯一运行标识。 */
    @Column(name = "run_id", nullable = false, unique = true, length = 64)
    private String runId;

    /** 固定任务名称。 */
    @Column(name = "job_name", nullable = false, length = 128)
    private String jobName;

    /** 当前任务的数据库级运行占用键，运行结束后清空。 */
    @Column(name = "active_lock_key", unique = true, length = 128)
    private String activeLockKey;

    /** 任务触发来源。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 32)
    private JobTriggerSource triggerSource;

    /** 触发操作人或系统标识。 */
    @Column(name = "triggered_by", length = 128)
    private String triggeredBy;

    /** 任务运行状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobExecutionStatus status;

    /** Quartz 计划触发时间。 */
    @Column(name = "scheduled_time")
    private LocalDateTime scheduledTime;

    /** 创建排队记录的时间。 */
    @Column(name = "queued_time", nullable = false)
    private LocalDateTime queuedTime;

    /** 实际开始时间。 */
    @Column(name = "started_time")
    private LocalDateTime startedTime;

    /** 实际结束时间。 */
    @Column(name = "finished_time")
    private LocalDateTime finishedTime;

    /** 执行耗时，单位为毫秒。 */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 脱敏后的异常类型。 */
    @Column(name = "error_type", length = 256)
    private String errorType;

    /** 脱敏后的错误摘要。 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 脱敏后的执行结果摘要。 */
    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    /** 执行节点名称。 */
    @Column(name = "host_name", length = 128)
    private String hostName;

    /** 记录创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
// AI_GENERATE_END --
