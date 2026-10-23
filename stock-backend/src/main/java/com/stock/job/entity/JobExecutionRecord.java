// AI_GENERATE_START---------
package com.stock.job.entity;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;

import org.springframework.data.annotation.Id;

import com.stock.job.enums.JobExecutionStatus;
import com.stock.job.enums.JobTriggerSource;

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
@Document(collection = "job_execution_record")
@CompoundIndex(name = "idx_job_execution_name_created", def = "{'jobName': 1, 'createdAt': -1}")
@Accessors(chain = true)
public class JobExecutionRecord {

    /** 数据库主键。 */
    @Id
    private Long id;

    /** 对外查询使用的唯一运行标识。 */
    @Indexed(unique = true, name = "uk_job_execution_run")
    private String runId;

    /** 固定任务名称。 */
    private String jobName;

    /** 任务触发来源。 */
    private JobTriggerSource triggerSource;

    /** 触发操作人或系统标识。 */
    private String triggeredBy;

    /** 任务运行状态。 */
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
    private String errorMessage;

    /** 脱敏后的执行结果摘要。 */
    private String resultSummary;

    /** 执行节点名称。 */
    private String hostName;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END---------
