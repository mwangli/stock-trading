// AI_GENERATE_START---
package com.stock.job.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
public class JobStopResponse {

    /** 被停止的运行标识。 */
    private String runId;

    /** 停止后的执行状态。 */
    private String status;

    /** 是否已向运行线程发送中断请求。 */
    private boolean interrupted;

    /** 停止结果说明。 */
    private String message;
}
// AI_GENERATE_END---
