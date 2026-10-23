// AI_GENERATE_START---
package com.stock.agent.runtime;

import lombok.Data;

import java.util.List;

/**
 * Research 结构化结果。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
public class ResearchResult implements AgentStructuredOutput {

    /** 研究摘要。 */
    private String summary;
    /** 研究状态，仅允许 PASS、WARN 或 UNKNOWN。 */
    private String status;
    /** 关键发现。 */
    private List<String> keyFindings;
    /** 证据引用。 */
    private List<String> evidenceRefs;
    /** 不确定性说明。 */
    private String uncertainty;
    /** 后续建议。 */
    private List<String> nextActions;
}
// AI_GENERATE_END---
