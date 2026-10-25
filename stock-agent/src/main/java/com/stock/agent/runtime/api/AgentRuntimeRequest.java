// AI_GENERATE_START-----------
package com.stock.agent.runtime.api;

import com.stock.agent.tooling.capability.AgentToolName;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Set;

/**
 * Agent Runtime 受控调用请求。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class AgentRuntimeRequest {

    /** 任务类型。 */
    String taskType;

    /** 会话标识。 */
    String sessionId;

    /** 任务标识。 */
    String taskId;

    /** 用户范围。 */
    String userScope;

    /** 系统约束提示词。 */
    String systemPrompt;

    /** 用户任务提示词。 */
    String userPrompt;

    /** 已由结构化查询服务提供的证据摘要。 */
    String evidenceSummary;

    /** 本次任务允许引用的证据编号。 */
    List<String> evidenceRefs;

    /** 本次任务允许绑定到 AiServices 的工具名称。 */
    Set<AgentToolName> allowedTools;

    /** 结构化输出类型。 */
    String outputType;
}
// AI_GENERATE_END----------
