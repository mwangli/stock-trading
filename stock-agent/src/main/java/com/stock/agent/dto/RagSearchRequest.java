// AI_GENERATE_START------
package com.stock.agent.dto;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 检索请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class RagSearchRequest {
    /** 检索问题。 */
    private String query;
    /** 最大结果数。 */
    private Integer limit;
    /** 来源类型，可选。 */
    private String sourceType;
    /** 来源版本，可选。 */
    private String version;
    /** 文档最早时间，可选。 */
    private LocalDateTime asOfFrom;
    /** 文档最晚时间，可选。 */
    private LocalDateTime asOfTo;
}
// AI_GENERATE_END------
