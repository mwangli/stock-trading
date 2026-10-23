// AI_GENERATE_START-----
package com.stock.agent.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 检索结果。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagResult {
    /** 文档分块标识。 */
    private String chunkId;
    /** 来源类型。 */
    private String sourceType;
    /** 来源标识。 */
    private String sourceId;
    /** 来源版本。 */
    private String version;
    /** 内容摘要。 */
    private String content;
    /** 相似度分数。 */
    private Double score;
    /** 内容时间。 */
    private LocalDateTime asOf;
}
// AI_GENERATE_END-----
