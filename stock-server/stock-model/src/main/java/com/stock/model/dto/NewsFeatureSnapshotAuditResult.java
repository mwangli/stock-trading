// AI_GENERATE_START -
package com.stock.model.dto;

/**
 * 新闻日级特征快照覆盖率和点时审计结果。
 *
 * @param expectedCount 当前行情来源版本要求的股票日数量
 * @param snapshotCount 已生成快照数量
 * @param coverageRate 快照覆盖率，范围 0 到 1
 * @param invalidContractCount 维度、日期或完整状态不符合契约的快照数
 * @param lookAheadCount 最晚输入事件晚于快照截止时间的数量
 * @param missingProvenanceCount 有事件但缺少管线或情感模型版本的数量
 * @param passed 是否达到覆盖率并通过点时审计
 * @author mwangli
 * @since 2026-10-25
 */
public record NewsFeatureSnapshotAuditResult(
        long expectedCount,
        long snapshotCount,
        double coverageRate,
        long invalidContractCount,
        long lookAheadCount,
        long missingProvenanceCount,
        boolean passed) {
}
// AI_GENERATE_END -
