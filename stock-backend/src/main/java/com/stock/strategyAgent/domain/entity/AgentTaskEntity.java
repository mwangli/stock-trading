// AI_GENERATE_START ----
package com.stock.strategyAgent.domain.entity;

import com.stock.strategyAgent.domain.AgentEnums;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "agent_task", indexes = {
        @Index(name = "idx_agent_task_session_time", columnList = "session_id,created_at"),
        @Index(name = "idx_agent_task_status", columnList = "status")
})
@Accessors(chain = true)
public class AgentTaskEntity {

    /** 任务唯一标识。 */
    @Id
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 所属会话标识。 */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /** 任务类型。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentEnums.TaskType taskType;

    /** 任务状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentEnums.TaskStatus status;

    /** 用户原始问题或任务说明。 */
    @Column(columnDefinition = "TEXT")
    private String question;

    /** 结构化任务结果或报告正文。 */
    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    /** 结构化结果类型。 */
    @Column(name = "result_type", length = 32)
    private String resultType;

    /** 脱敏后的结构化结果 JSON。 */
    @Column(name = "structured_result", columnDefinition = "TEXT")
    private String structuredResult;

    /** 证据摘要 JSON 或文本。 */
    @Column(name = "evidence_summary", columnDefinition = "TEXT")
    private String evidenceSummary;

    /** 证据编号列表，使用 JSON 文本保存。 */
    @Column(name = "evidence_refs", columnDefinition = "TEXT")
    private String evidenceRefs;

    /** 不确定性摘要。 */
    @Column(columnDefinition = "TEXT")
    private String uncertainty;

    /** 脱敏后的失败原因。 */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /** 任务创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 任务更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END ----
