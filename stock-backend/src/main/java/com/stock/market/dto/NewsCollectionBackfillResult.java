// AI_GENERATE_START---
package com.stock.market.dto;

import java.util.Map;

/**
 * 新闻采集治理历史回填结果。
 *
 * @param pipelineVersion 管线版本
 * @param scannedCount 扫描文档数
 * @param updatedCount 更新原始文档数
 * @param eventFactCount 同步事件事实数
 * @param duplicateCount 识别出的重复文档数
 * @param ignoredCount 忽略文档数
 * @param documentTypeCounts 按真实文档类型统计的数量
 * @param documentRoleCounts 按业务角色统计的数量
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
        long duplicateCount,
        long ignoredCount,
        Map<String, Long> documentTypeCounts,
        Map<String, Long> documentRoleCounts,
        long failedCount,
        String lastProcessedId,
        boolean completed) {
}
// AI_GENERATE_END---
