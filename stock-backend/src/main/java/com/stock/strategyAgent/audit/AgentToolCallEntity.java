// AI_GENERATE_START -
package com.stock.strategyAgent.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 工具调用脱敏审计记录。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Accessors(chain = true)
@Entity
@Table(name = "agent_tool_call", indexes = {
        @Index(name = "idx_agent_tool_call_task_time", columnList = "task_id,started_at"),
        @Index(name = "idx_agent_tool_call_tool_time", columnList = "tool_name,started_at")
})
public class AgentToolCallEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 追踪标识。 */
    @Column(name = "trace_id", length = 64)
    private String traceId;

    /** 会话标识。 */
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 任务标识。 */
    @Column(name = "task_id", length = 64)
    private String taskId;

    /** 工具名称。 */
    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    /** 工具版本。 */
    @Column(name = "tool_version", nullable = false, length = 32)
    private String toolVersion;

    /** 执行状态。 */
    @Column(nullable = false, length = 32)
    private String status;

    /** 错误码。 */
    @Column(name = "error_code", length = 64)
    private String errorCode;

    /** 脱敏参数摘要。 */
    @Column(name = "parameter_summary", length = 2000)
    private String parameterSummary;

    /** 脱敏结果摘要。 */
    @Column(name = "result_summary", length = 2000)
    private String resultSummary;

    /** 开始时间。 */
    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 结束时间。 */
    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
// AI_GENERATE_END -
