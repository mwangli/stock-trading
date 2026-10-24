// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

/**
 * Backend 任务诊断 HTTP 响应。
 *
 * @param jobName 任务名称
 * @param status 最近运行状态
 * @param errorMessage 脱敏错误摘要
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendJobDiagnosticResponse(String jobName, String status, String errorMessage) {
}
// AI_GENERATE_END --
