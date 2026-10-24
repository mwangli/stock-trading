// AI_GENERATE_START--
package com.stock.platform.dto;

import java.time.LocalDateTime;

/**
 * Agent 使用的模型运维概览。
 *
 * @param modelName 模型名称
 * @param activeVersionId 激活版本标识
 * @param activeVersion 激活版本号
 * @param activeVersionStatus 激活版本状态
 * @param activeModelHealthy 激活模型是否健康
 * @param trainingRunning 是否存在运行中训练
 * @param latestTrainingRunId 最近训练标识
 * @param latestTrainingStatus 最近训练状态
 * @param latestTrainingCreatedAt 最近训练创建时间
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalModelOverviewResponse(
        String modelName, String activeVersionId, String activeVersion,
        String activeVersionStatus, boolean activeModelHealthy, boolean trainingRunning,
        String latestTrainingRunId, String latestTrainingStatus,
        LocalDateTime latestTrainingCreatedAt, LocalDateTime asOf) {
}
// AI_GENERATE_END--
