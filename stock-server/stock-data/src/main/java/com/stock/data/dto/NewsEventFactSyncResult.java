// AI_GENERATE_START---
package com.stock.data.dto;

/**
 * 新闻事件事实批量同步结果。
 *
 * @param eligibleDocumentCount 可生成事件事实的原始文档数
 * @param insertedEventCount 新增的规范事件事实数
 * @param matchedEventCount 命中既有规范事件事实的文档数
 * @param duplicateDocumentCount 未形成新增事实的重复文档数
 * @author mwangli
 * @since 2026-10-24
 */
public record NewsEventFactSyncResult(
        long eligibleDocumentCount,
        long insertedEventCount,
        long matchedEventCount,
        long duplicateDocumentCount) {

    /**
     * 创建空同步结果。
     *
     * @return 所有计数均为零的同步结果
     */
    public static NewsEventFactSyncResult empty() {
        return new NewsEventFactSyncResult(0, 0, 0, 0);
    }
}
// AI_GENERATE_END---
