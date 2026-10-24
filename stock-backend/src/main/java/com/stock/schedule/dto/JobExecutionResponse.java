// AI_GENERATE_START----
package com.stock.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 执行记录详情。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobExecutionResponse {

    /** 运行标识。 */
    private String runId;

    /** 任务名称。 */
    private String jobName;

    /** 触发来源。 */
    private String triggerSource;

    /** 触发操作人。 */
    private String triggeredBy;

    /** 运行状态。 */
    private String status;

    /** 计划触发时间。 */
    private LocalDateTime scheduledTime;

    /** 开始时间。 */
    private LocalDateTime startedTime;

    /** 结束时间。 */
    private LocalDateTime finishedTime;

    /** 执行耗时，单位为毫秒。 */
    private Long durationMs;

    /** 异常类型。 */
    private String errorType;

    /** 错误摘要。 */
    private String errorMessage;

    /** 结果摘要。 */
    private String resultSummary;

    /** 执行节点。 */
    private String hostName;

    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END----
