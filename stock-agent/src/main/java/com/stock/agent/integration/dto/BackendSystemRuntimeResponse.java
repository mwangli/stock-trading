// AI_GENERATE_START --
package com.stock.agent.integration.dto;

import java.time.LocalDateTime;

/**
 * Backend 系统运行事实线缆响应。
 *
 * @param backendStatus Backend 进程状态
 * @param nodeId 当前节点标识
 * @param schedulerEnabled Quartz 是否允许执行
 * @param startupDataSyncEnabled 启动数据同步是否启用
 * @param automaticTradingPaused 是否紧急停止后续自动交易
 * @param emergencyStopReason 紧急停止原因
 * @param emergencyStoppedAt 紧急停止时间
 * @param emergencyStopResult 紧急停止结果
 * @param updatedAt 配置更新时间
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-25
 */
public record BackendSystemRuntimeResponse(
        String backendStatus, String nodeId, boolean schedulerEnabled,
        boolean startupDataSyncEnabled, boolean automaticTradingPaused,
        String emergencyStopReason, LocalDateTime emergencyStoppedAt,
        String emergencyStopResult, LocalDateTime updatedAt, LocalDateTime asOf) {
}
// AI_GENERATE_END --
