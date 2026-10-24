// AI_GENERATE_START--
package com.stock.platform.dto;

/**
 * Agent 使用的任务诊断摘要。
 *
 * @param jobName 任务名称
 * @param status 最近运行状态
 * @param errorMessage 脱敏错误摘要
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalJobDiagnosticResponse(String jobName, String status, String errorMessage) {
}
// AI_GENERATE_END--
