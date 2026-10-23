// AI_GENERATE_START -
package com.stock.data.dto;

/**
 * 新闻采集治理历史回填结果。
 *
 * @param pipelineVersion 管线版本
 * @param scannedCount 扫描文档数
 * @param updatedCount 更新原始文档数
 * @param eventFactCount 同步事件事实数
 * @param ignoredCount 忽略文档数
 * @param failedCount 失败文档数
 * @param lastProcessedId 最后处理的 MongoDB 文档 ID
 * @param completed 当前版本是否全部完成
 * @author mwangli
 * @since 2026-10-23
 */
public record NewsCollectionBackfillResult(
        String pipelineVersion,
        long scannedCount,
        long updatedCount,
        long eventFactCount,
        long ignoredCount,
        long failedCount,
        String lastProcessedId,
        boolean completed) {
}
// AI_GENERATE_END -
