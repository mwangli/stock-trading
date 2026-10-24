// AI_GENERATE_START---
package com.stock.platform.dto;

/**
 * 跨模块使用的任务执行脱敏摘要。
 * 仅暴露任务诊断需要的状态和错误信息，不泄露调度持久化实体。
 *
 * @param jobName 任务名称
 * @param status 执行状态
 * @param errorMessage 脱敏错误摘要
 * @author mwangli
 * @since 2026-10-23
 */
public record JobExecutionSnapshot(String jobName, String status, String errorMessage) {
}
// AI_GENERATE_END---
