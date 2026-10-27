// AI_GENERATE_START-----
package com.stock.agent.output;

import lombok.Data;

import java.util.List;

/**
 * Diagnostic 结构化结果。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
public class DiagnosticResult implements AgentStructuredOutput {

    /** 诊断摘要。 */
    private String summary;
    /** 诊断状态，仅允许 PASS、WARN、BLOCKED 或 UNKNOWN。 */
    private String status;
    /** 检查项。 */
    private List<DiagnosticCheck> checks;
    /** 阻断原因。 */
    private List<String> blockedReasons;
    /** 证据引用。 */
    private List<String> evidenceRefs;
    /** 不确定性说明。 */
    private String uncertainty;

    /** 单项诊断检查结果。 */
    @Data
    public static class DiagnosticCheck {
        /** 检查项名称。 */
        private String name;
        /** 检查项状态。 */
        private String status;
        /** 检查项详情。 */
        private String detail;
        /** 检查项证据引用。 */
        private List<String> evidenceRefs;
    }
}
// AI_GENERATE_END----