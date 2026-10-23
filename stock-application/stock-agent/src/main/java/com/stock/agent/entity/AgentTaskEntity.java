// AI_GENERATE_START--------
package com.stock.agent.entity;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

import com.stock.agent.domain.AgentEnums;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 任务实体。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Document(collection = "agent_task")
@Accessors(chain = true)
public class AgentTaskEntity {

    /** 任务唯一标识。 */
    @Id
    private String taskId;

    /** 所属会话标识。 */
    private String sessionId;

    /** 任务类型。 */
    private AgentEnums.TaskType taskType;

    /** 任务状态。 */
    private AgentEnums.TaskStatus status;

    /** 用户原始问题或任务说明。 */
    private String question;

    /** 结构化任务结果或报告正文。 */
    private String resultSummary;

    /** 结构化结果类型。 */
    private String resultType;

    /** 脱敏后的结构化结果 JSON。 */
    private String structuredResult;

    /** 证据摘要 JSON 或文本。 */
    private String evidenceSummary;

    /** 证据编号列表，使用 JSON 文本保存。 */
    private String evidenceRefs;

    /** 不确定性摘要。 */
    private String uncertainty;

    /** 脱敏后的失败原因。 */
    private String errorMessage;

    /** 任务创建时间。 */
    private LocalDateTime createdAt;

    /** 任务更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END--------
