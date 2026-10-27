// AI_GENERATE_START--------
package com.stock.agent.entity;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

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
@Document(collection = "agent_tool_call")
public class AgentToolCallEntity {

    /** 数据库主键。 */
    @Id
    private String id;

    /** 追踪标识。 */
    private String traceId;

    /** 会话标识。 */
    private String sessionId;

    /** 任务标识。 */
    private String taskId;

    /** 工具名称。 */
    private String toolName;

    /** 工具版本。 */
    private String toolVersion;

    /** 执行状态。 */
    private String status;

    /** 错误码。 */
    private String errorCode;

    /** 脱敏参数摘要。 */
    private String parameterSummary;

    /** 脱敏结果摘要。 */
    private String resultSummary;

    /** 开始时间。 */
    private LocalDateTime startedAt;

    /** 结束时间。 */
    private LocalDateTime finishedAt;
}
// AI_GENERATE_END-------