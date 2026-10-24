// AI_GENERATE_START----
package com.stock.schedule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class JobTriggerResponse {

    /** 本次新建或已存在的运行标识。 */
    private String runId;

    /** 是否接受并创建了新的任务运行。 */
    private boolean accepted;

    /** 当前任务运行状态。 */
    private String status;

    /** 提示消息。 */
    private String message;
}
// AI_GENERATE_END----
