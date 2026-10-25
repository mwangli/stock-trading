// AI_GENERATE_START -
package com.stock.agent.tooling.tool.read.dto;

import java.time.LocalDateTime;

/**
 * 业务知识库检索命中的单个受控片段。
 *
 * @param chunkId Chunk 唯一标识，不可为空
 * @param sourceType 来源类型，不可为空
 * @param sourceId 来源业务标识，不可为空
 * @param version 来源版本，不可为空
 * @param content 受长度限制的知识正文，不超过 2000 字符
 * @param score 余弦相似度，可为空
 * @param asOf 资料事实时间，可为空
 * @author mwangli
 * @since 2026-10-25
 */
public record KnowledgeSearchItem(
        String chunkId,
        String sourceType,
        String sourceId,
        String version,
        String content,
        Double score,
        LocalDateTime asOf) {
}
// AI_GENERATE_END -
