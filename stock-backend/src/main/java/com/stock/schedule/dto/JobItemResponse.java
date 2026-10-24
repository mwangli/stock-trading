// AI_GENERATE_START----
package com.stock.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class JobItemResponse {

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

    /** 任务运行状态：ENABLED、STOPPED、RUNNING 或 NOT_REGISTERED。 */
    private String taskStatus;

    /** 最近一次执行记录。 */
    private JobExecutionResponse latestRun;
}
// AI_GENERATE_END----
