// AI_GENERATE_START ---
package com.stock.strategyAgent.api;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * RAG 文档写入请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class RagIngestRequest {
    /** 文档所属用户范围，PRIVATE 文档只允许同范围检索。 */
    private String userScope;
    /** 来源类型。 */
    private String sourceType;
    /** 来源标识。 */
    private String sourceId;
    /** 来源版本。 */
    private String version;
    /** 文档内容。 */
    private String content;
    /** 股票代码。 */
    private List<String> stockCodes;
    /** 文档标签。 */
    private List<String> tags;
    /** 可见范围。 */
    private String visibility;
}
// AI_GENERATE_END ---
