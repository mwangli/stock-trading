// AI_GENERATE_START -----
package com.stock.strategyAgent.audit;

import com.stock.strategyAgent.tool.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

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
     * @param toolName 工具名称
     * @param context 调用上下文
     * @param status 执行状态
     * @param errorCode 错误码
     * @param startedAt 开始时间
     */
    public void record(String toolName, ToolExecutionContext context, String status,
                       String errorCode, LocalDateTime startedAt) {
        if (!StringUtils.hasText(toolName)) {
            return;
        }
        repository.save(new AgentToolCallEntity()
                .setTraceId(context == null ? null : context.getTraceId())
                .setSessionId(context == null ? null : context.getSessionId())
                .setTaskId(context == null ? null : context.getTaskId())
                .setToolName(toolName)
                .setToolVersion("v1")
                .setStatus(status)
                .setErrorCode(errorCode)
                .setParameterSummary(context == null ? null : limit("scope=" + context.getUserScope()
                        + ", sessionId=" + context.getSessionId() + ", taskId=" + context.getTaskId()))
                .setResultSummary(null)
                .setStartedAt(startedAt)
                .setFinishedAt(LocalDateTime.now()));
    }

    /**
     * 查询任务实际成功调用的工具名称。
     *
     * @param taskId 任务标识
     * @return 去重后的工具名称
     */
    public List<String> successfulToolNames(String taskId) {
        return repository.findByTaskIdAndStatusOrderByStartedAtAsc(taskId, "SUCCESS").stream()
                .map(AgentToolCallEntity::getToolName)
                .distinct()
                .toList();
    }

    /**
     * 将成功工具调用转换为可追踪的审计证据编号。
     *
     * @param taskId 任务标识
     * @return 工具审计证据编号
     */
    public List<String> successfulEvidenceRefs(String taskId) {
        return repository.findByTaskIdAndStatusOrderByStartedAtAsc(taskId, "SUCCESS").stream()
                .map(item -> "toolCall:" + item.getId())
                .toList();
    }

    private String limit(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
// AI_GENERATE_END -----
