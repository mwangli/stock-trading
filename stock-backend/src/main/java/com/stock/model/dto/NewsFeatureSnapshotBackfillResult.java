// AI_GENERATE_START -
package com.stock.model.dto;

/**
 * 新闻日级特征快照回填结果。
 *
 * @param scannedCount 本次扫描模型日线数量
 * @param generatedCount 本次生成快照数量
 * @param skippedCount 本次跳过已存在快照数量
 * @param failedCount 本次无效模型日线数量
 * @param lastProcessedBarId 最后处理的模型日线文档 ID
 * @param completed 是否已经完成全量回填并通过点时审计
 * @param audit 点时与覆盖率审计结果
 * @author mwangli
 * @since 2026-10-25
 */
public record NewsFeatureSnapshotBackfillResult(
        long scannedCount,
        long generatedCount,
        long skippedCount,
        long failedCount,
        String lastProcessedBarId,
        boolean completed,
        NewsFeatureSnapshotAuditResult audit) {
}
// AI_GENERATE_END -
