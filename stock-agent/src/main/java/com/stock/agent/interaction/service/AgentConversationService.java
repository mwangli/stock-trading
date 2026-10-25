// AI_GENERATE_START----------
package com.stock.agent.interaction.service;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.interaction.dto.AgentChatRequest;
import com.stock.agent.interaction.dto.AgentChatResponse;
import com.stock.agent.interaction.dto.AgentChatStatus;
import com.stock.agent.interaction.dto.AgentTaskStrategy;
import com.stock.agent.interaction.dto.DiagnosticRequest;
import com.stock.agent.interaction.dto.ResearchRequest;
import com.stock.agent.interaction.dto.TaskResponse;
import com.stock.agent.runtime.concurrency.AgentConversationLockService;
import com.stock.agent.tooling.audit.service.AgentToolAuditService;
import com.stock.agent.tooling.capability.AgentCapabilityRouter;
import com.stock.agent.tooling.capability.AgentCapabilitySelection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Agent 统一聊天编排服务。
 * 负责后端策略选择、同会话串行执行以及统一响应组装。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Service
@RequiredArgsConstructor
public class AgentConversationService {

    private final AgentCapabilityRouter capabilityRouter;
    private final AgentApplicationService applicationService;
    private final AgentConversationLockService conversationLockService;
    private final AgentToolAuditService toolAuditService;

    /**
     * 执行一轮统一 Agent 聊天。
     *
     * @param request 聊天请求
     * @param userScope 服务端认证用户范围
     * @return 统一聊天响应
     */
    public AgentChatResponse chat(AgentChatRequest request, String userScope) {
        String question = requireQuestion(request == null ? null : request.getQuestion());
        AgentCapabilitySelection selection = capabilityRouter.route(question);
        String conversationKey = userScope + ":" + (StringUtils.hasText(request.getSessionId())
                ? request.getSessionId().trim() : "new-session");
        return conversationLockService.execute(conversationKey,
                () -> execute(request, userScope, question, selection));
    }

    private AgentChatResponse execute(AgentChatRequest request, String userScope, String question,
                                      AgentCapabilitySelection selection) {
        TaskResponse task = selection.strategy() == AgentTaskStrategy.DIAGNOSTIC
                ? applicationService.diagnose(toDiagnosticRequest(request, question), userScope,
                        selection.allowedTools())
                : applicationService.conversation(toResearchRequest(request, question), userScope,
                        selection.allowedTools());
        List<String> evidenceRefs = mergeEvidenceRefs(
                parseEvidenceRefs(task.getEvidenceRefs()), toolAuditService.successfulEvidenceRefs(task.getTaskId()));
        return AgentChatResponse.builder()
                .sessionId(task.getSessionId())
                .taskId(task.getTaskId())
                .answer(resolveAnswer(task))
                .strategy(selection.strategy())
                .status(toStatus(task.getStatus()))
                .evidenceRefs(evidenceRefs)
                .capabilities(toolAuditService.successfulToolNames(task.getTaskId()))
                .uncertainty(task.getUncertainty())
                .createdAt(task.getUpdatedAt() == null ? LocalDateTime.now() : task.getUpdatedAt())
                .build();
    }

    private ResearchRequest toResearchRequest(AgentChatRequest request, String question) {
        ResearchRequest target = new ResearchRequest();
        target.setSessionId(request.getSessionId());
        target.setQuestion(question);
        target.setStockCode(request.getStockCode());
        target.setCandidateBatchId(request.getCandidateBatchId());
        target.setPredictionBatchId(request.getPredictionBatchId());
        target.setStrategyVersion(request.getStrategyVersion());
        target.setTargetTradeDate(request.getTargetTradeDate());
        return target;
    }

    private DiagnosticRequest toDiagnosticRequest(AgentChatRequest request, String question) {
        DiagnosticRequest target = new DiagnosticRequest();
        target.setSessionId(request.getSessionId());
        target.setQuestion(question);
        if (containsTradeDiagnostic(question)) {
            target.setJobName("realTradingAutoBuy");
        }
        return target;
    }

    private boolean containsTradeDiagnostic(String question) {
        return question.contains("买入") || question.contains("下单") || question.contains("执行门禁");
    }

    private String resolveAnswer(TaskResponse task) {
        if (StringUtils.hasText(task.getResultSummary())) {
            return task.getResultSummary();
        }
        if (StringUtils.hasText(task.getErrorMessage())) {
            return task.getErrorMessage();
        }
        return "Agent 未返回可用结果";
    }

    private AgentChatStatus toStatus(String status) {
        if ("COMPLETED".equals(status)) {
            return AgentChatStatus.COMPLETED;
        }
        if ("DEGRADED".equals(status)) {
            return AgentChatStatus.DEGRADED;
        }
        return AgentChatStatus.FAILED;
    }

    private List<String> parseEvidenceRefs(String evidenceRefs) {
        if (!StringUtils.hasText(evidenceRefs)) {
            return List.of();
        }
        try {
            List<String> values = JSON.parseArray(evidenceRefs, String.class);
            return values == null ? List.of() : Collections.unmodifiableList(values);
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<String> mergeEvidenceRefs(List<String> taskRefs, List<String> toolRefs) {
        LinkedHashSet<String> merged = new LinkedHashSet<>(taskRefs);
        merged.addAll(toolRefs);
        return List.copyOf(merged);
    }

    private String requireQuestion(String question) {
        if (!StringUtils.hasText(question)) {
            throw new IllegalArgumentException("Agent 问题不能为空");
        }
        String normalized = question.trim();
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("Agent 问题不能超过 2000 个字符");
        }
        return normalized;
    }
}
// AI_GENERATE_END---------
