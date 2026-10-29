// AI_GENERATE_START ---
package com.stock.model.dto;

/**
 * 新闻日期完备性水位和稀疏特征快照审计结果。
 *
 * @param expectedDateCount 当前行情来源版本包含的交易日数量
 * @param completeDateCount 已达到 COMPLETE 水位的交易日数量
 * @param dateCompletionRate 日期水位完成率，范围 0 到 1
 * @param sparseSnapshotCount 已持久化的有事件稀疏快照数量
 * @param invalidContractCount 维度、日期或完整状态不符合契约的稀疏快照数
 * @param lookAheadCount 最晚输入事件晚于快照截止时间的数量
 * @param missingProvenanceCount 有事件但缺少管线或情感模型版本的数量
 * @param passed 是否所有要求日期均完成且通过契约、点时和追溯审计
 * @author mwangli
 * @since 2026-10-25
 */
public record NewsFeatureSnapshotAuditResult(
        long expectedDateCount,
        long completeDateCount,
        double dateCompletionRate,
        long sparseSnapshotCount,
        long invalidContractCount,
        long lookAheadCount,
        long missingProvenanceCount,
        boolean passed) {
}
// AI_GENERATE_END ---
