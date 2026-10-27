// AI_GENERATE_START-------------
package com.stock.agent.service;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.entity.AgentToolCallEntity;
import com.stock.agent.repository.AgentToolCallRepository;

import com.stock.agent.model.ToolExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
     * @param evidenceRefs 工具实际返回的来源引用
     * @param startedAt 开始时间
     */
    public void record(String toolName, ToolExecutionContext context, String status,
                       String errorCode, List<String> evidenceRefs, LocalDateTime startedAt) {
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
                .setResultSummary(serializeEvidenceRefs(evidenceRefs))
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
        LinkedHashSet<String> references = new LinkedHashSet<>();
        repository.findByTaskIdAndStatusOrderByStartedAtAsc(taskId, "SUCCESS").forEach(item -> {
            references.add("toolCall:" + item.getId());
            references.addAll(parseEvidenceRefs(item.getResultSummary()));
        });
        return List.copyOf(references);
    }

    private String serializeEvidenceRefs(List<String> evidenceRefs) {
        List<String> sanitized = sanitizeEvidenceRefs(evidenceRefs);
        return sanitized.isEmpty() ? null : limit(JSON.toJSONString(sanitized));
    }

    private List<String> parseEvidenceRefs(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return sanitizeEvidenceRefs(JSON.parseArray(value, String.class));
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<String> sanitizeEvidenceRefs(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> sanitized = new ArrayList<>();
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim();
            if (normalized.startsWith("documents/") || normalized.startsWith("rag:")
                    || normalized.startsWith("https://")
                    || normalized.startsWith("http://")) {
                sanitized.add(limit(normalized));
            }
        }
        return sanitized.stream().distinct().limit(5).toList();
    }

    private String limit(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }
}
// AI_GENERATE_END------------