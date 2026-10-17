// AI_GENERATE_START --
package com.stock.strategyAgent.audit;

import com.stock.strategyAgent.tool.ToolDefinition;
import com.stock.strategyAgent.tool.ToolExecutionContext;
import com.stock.strategyAgent.tool.ToolExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Agent 工具调用审计服务。
 * 只保存长度受限的摘要，不保存原始参数、密钥或完整工具响应。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class AgentToolAuditService {

    private final AgentToolCallRepository repository;

    /**
     * 保存一次工具调用摘要。
     *
     * @param definition 工具定义
     * @param context 调用上下文
     * @param result 工具结果
     * @param startedAt 开始时间
     */
    public void record(ToolDefinition definition, ToolExecutionContext context,
                       ToolExecutionResult<?> result, LocalDateTime startedAt) {
        if (definition == null || result == null) {
            return;
        }
        repository.save(new AgentToolCallEntity()
                .setTraceId(context == null ? null : context.getTraceId())
                .setSessionId(context == null ? null : context.getSessionId())
                .setTaskId(context == null ? null : context.getTaskId())
                .setToolName(definition.getName())
                .setToolVersion(definition.getVersion())
                .setStatus(result.getStatus())
                .setErrorCode(result.getErrorCode())
                .setParameterSummary(context == null ? null : limit("scope=" + context.getUserScope()
                        + ", sessionId=" + context.getSessionId() + ", taskId=" + context.getTaskId()))
                .setResultSummary(limit(result.getWarnings()))
                .setStartedAt(startedAt)
                .setFinishedAt(LocalDateTime.now()));
    }

    private String limit(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
// AI_GENERATE_END --
