// AI_GENERATE_START ---
package com.stock.strategyAgent.api;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Diagnostic 任务请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class DiagnosticRequest {
    /** 会话标识，可为空。 */
    private String sessionId;
    /** 需要诊断的任务名称，可为空。 */
    private String jobName;
    /** 诊断说明。 */
    private String question;
    /** 用户范围。 */
    private String userScope;
}
// AI_GENERATE_END ---
