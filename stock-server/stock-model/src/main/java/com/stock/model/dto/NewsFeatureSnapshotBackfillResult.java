// AI_GENERATE_START --
package com.stock.model.dto;

import java.time.LocalDate;

/**
 * 新闻日级特征稀疏准备结果。
 *
 * @param processedDateCount 本次完成准备的来源日期数量
 * @param generatedSnapshotCount 本次生成的有事件稀疏快照数量
 * @param existingSnapshotCount 本次复用的已有稀疏快照数量
 * @param failedDateCount 本次失败日期数量
 * @param lastProcessedDate 最后成功完成的特征交易日
 * @param completed 是否已经完成全部日期准备并通过点时审计
 * @param audit 日期完备性与稀疏快照审计结果
 * @author mwangli
 * @since 2026-10-25
 */
public record NewsFeatureSnapshotBackfillResult(
        long processedDateCount,
        long generatedSnapshotCount,
        long existingSnapshotCount,
        long failedDateCount,
        LocalDate lastProcessedDate,
        boolean completed,
        NewsFeatureSnapshotAuditResult audit) {
}
// AI_GENERATE_END --
