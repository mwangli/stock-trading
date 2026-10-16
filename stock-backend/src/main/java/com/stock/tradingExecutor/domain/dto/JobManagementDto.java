// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务管理接口 DTO 集合。
 *
 * @author mwangli
 * @since 2026-10-11
 */
public final class JobManagementDto {

    private JobManagementDto() {
    }

    /**
     * 任务列表响应。
     *
     * @author mwangli
     * @since 2026-10-11
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobListResponse {

        /** 任务列表。 */
        private List<JobItem> items;
    }

    /**
     * 任务状态列表项。
     *
     * @author mwangli
     * @since 2026-10-11
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobItem {

        /** 任务名称。 */
        private String jobName;

        /** 任务描述。 */
        private String description;

        /** Cron 表达式。 */
        private String cronExpression;

        /** 所属业务模块。 */
        private String module;

        /** 同步模式或任务类型。 */
        private String syncMode;

        /** 是否启用。 */
        private boolean enabled;

        /** 任务运行状态：ENABLED、STOPPED、QUEUED、RUNNING 或 NOT_REGISTERED。 */
        private String taskStatus;

        /** 最近一次执行记录。 */
        private ExecutionItem latestRun;
    }

    /**
     * 执行记录分页响应。
     *
     * @author mwangli
     * @since 2026-10-11
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExecutionPageResponse {

        /** 当前页数据。 */
        private List<ExecutionItem> items;

        /** 总记录数。 */
        private long total;

        /** 当前页码，从 1 开始。 */
        private int current;

        /** 每页大小。 */
        private int pageSize;
    }

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
    public static class ExecutionItem {

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

        /** 排队时间。 */
        private LocalDateTime queuedTime;

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

    /**
     * 任务触发响应。
     *
     * @author mwangli
     * @since 2026-10-11
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TriggerResponse {

        /** 新建的运行标识。 */
        private String runId;

        /** 提示消息。 */
        private String message;
    }

    /**
     * 停止任务执行响应。
     *
     * @author mwangli
     * @since 2026-10-11
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StopResponse {

        /** 被停止的运行标识。 */
        private String runId;

        /** 停止后的执行状态。 */
        private String status;

        /** 是否已向运行线程发送中断请求。 */
        private boolean interrupted;

        /** 停止结果说明。 */
        private String message;
    }
}
// AI_GENERATE_END --
