// AI_GENERATE_START-----
package com.stock.agent.output;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 每日投研报告结构化结果。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
public class ReportResult implements AgentStructuredOutput {

    /** 报告日期，格式为 yyyy-MM-dd。 */
    private String reportDate;
    /** 报告摘要。 */
    private String summary;
    /** 报告状态，仅允许 PASS、WARN 或 UNKNOWN。 */
    private String status;
    /** 固定报告章节。 */
    private Map<String, String> sections;
    /** 数据质量摘要。 */
    private String dataQuality;
    /** 证据引用。 */
    private List<String> evidenceRefs;
    /** 不确定性说明。 */
    private String uncertainty;
}
// AI_GENERATE_END----