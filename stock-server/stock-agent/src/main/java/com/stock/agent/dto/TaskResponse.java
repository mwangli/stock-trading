// AI_GENERATE_START-------
package com.stock.agent.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务响应。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    /** 任务标识。 */
    private String taskId;
    /** 会话标识。 */
    private String sessionId;
    /** 任务类型。 */
    private String taskType;
    /** 任务状态。 */
    private String status;
    /** 原始问题。 */
    private String question;
    /** 任务结果摘要。 */
    private String resultSummary;
    /** 结构化结果类型。 */
    private String resultType;
    /** 脱敏后的结构化结果 JSON。 */
    private String structuredResult;
    /** 证据摘要。 */
    private String evidenceSummary;
    /** 证据编号列表。 */
    private String evidenceRefs;
    /** 不确定性摘要。 */
    private String uncertainty;
    /** 错误摘要。 */
    private String errorMessage;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END------