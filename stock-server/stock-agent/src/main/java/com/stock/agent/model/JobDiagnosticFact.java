// AI_GENERATE_START ---
package com.stock.agent.model;

/**
 * Backend 返回的任务诊断事实。
 *
 * @param jobName 任务名称
 * @param status 最近运行状态
 * @param errorMessage 脱敏错误摘要
 * @author mwangli
 * @since 2026-10-23
 */
public record JobDiagnosticFact(String jobName, String status, String errorMessage) {
}
// AI_GENERATE_END --