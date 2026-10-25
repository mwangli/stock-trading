// AI_GENERATE_START -
package com.stock.agent.knowledge.rag.source;

/**
 * 单个业务来源文档的 RAG 同步结果。
 *
 * @param sourceType 来源类型
 * @param sourceId 来源标识
 * @param version 内容版本
 * @param chunkCount 当前版本分块数量
 * @param changed 是否写入了新版本
 * @author mwangli
 * @since 2026-10-25
 */
public record RagSourceSyncResult(
        String sourceType,
        String sourceId,
        String version,
        int chunkCount,
        boolean changed) {
}
// AI_GENERATE_END -
