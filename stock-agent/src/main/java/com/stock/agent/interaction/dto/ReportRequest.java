// AI_GENERATE_START-------
package com.stock.agent.interaction.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 日报生成请求。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@NoArgsConstructor
public class ReportRequest {
    /** 会话标识，可为空。 */
    private String sessionId;
    /** 报告日期，格式为 yyyy-MM-dd。 */
    private String reportDate;
}
// AI_GENERATE_END------
