// AI_GENERATE_START----
package com.stock.agent.controller;

import com.stock.agent.dto.AgentChatRequest;
import com.stock.agent.dto.AgentChatResponse;
import com.stock.agent.dto.CreateSessionRequest;
import com.stock.agent.dto.ReportRequest;
import com.stock.agent.dto.SessionResponse;
import com.stock.agent.dto.TaskResponse;
import com.stock.agent.service.AgentApplicationService;
import com.stock.agent.service.AgentConversationService;
import com.stock.agent.service.AgentSessionService;
import com.stock.agent.service.AgentTaskService;
import com.stock.agent.api.ResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 会话、聊天、报告和异步任务控制器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentConversationController {

    private final AgentApplicationService applicationService;
    private final AgentConversationService conversationService;
    private final AgentSessionService sessionService;
    private final AgentTaskService taskService;
    private final AgentRequestUserResolver requestUserResolver;

    /**
     * 创建当前用户的 Agent 会话。
     *
     * @param request 创建请求
     * @param httpRequest HTTP 请求
     * @return 会话信息
     */
    @PostMapping("/sessions")
    public ResponseDTO<SessionResponse> createSession(@RequestBody CreateSessionRequest request,
                                                       HttpServletRequest httpRequest) {
        log.info("[Agent] 创建当前用户会话");
        return ResponseDTO.success(sessionService.create(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 查询当前用户的 Agent 会话。
     *
     * @param sessionId 会话标识
     * @param httpRequest HTTP 请求
     * @return 会话信息
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseDTO<SessionResponse> getSession(@PathVariable String sessionId,
                                                    HttpServletRequest httpRequest) {
        log.info("[Agent] 查询会话: sessionId={}", sessionId);
        return ResponseDTO.success(sessionService.get(
                sessionId, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 执行统一 Agent 聊天。
     *
     * @param request 聊天请求
     * @param httpRequest HTTP 请求
     * @return 聊天响应
     */
    @PostMapping("/chat")
    public ResponseDTO<AgentChatResponse> chat(@RequestBody AgentChatRequest request,
                                               HttpServletRequest httpRequest) {
        log.info("[Agent] 执行统一聊天: sessionId={}, questionLength={}",
                request == null ? null : request.getSessionId(),
                request == null || request.getQuestion() == null ? 0 : request.getQuestion().length());
        return ResponseDTO.success(conversationService.chat(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 生成当前用户的每日投研报告。
     *
     * @param request 报告请求
     * @param httpRequest HTTP 请求
     * @return 报告任务
     */
    @PostMapping("/reports/daily")
    public ResponseDTO<TaskResponse> dailyReport(@RequestBody ReportRequest request,
                                                  HttpServletRequest httpRequest) {
        log.info("[Agent] 生成每日投研报告");
        return ResponseDTO.success(applicationService.dailyReport(
                request, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 查询 Agent 任务详情。
     *
     * @param taskId 任务标识
     * @param httpRequest HTTP 请求
     * @return 任务详情
     */
    @GetMapping("/tasks/details/{taskId}")
    public ResponseDTO<TaskResponse> getTask(@PathVariable String taskId,
                                             HttpServletRequest httpRequest) {
        log.info("[Agent] 查询任务: taskId={}", taskId);
        return ResponseDTO.success(taskService.get(
                taskId, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 查询 Agent 任务证据摘要。
     *
     * @param taskId 任务标识
     * @param httpRequest HTTP 请求
     * @return 包含证据摘要的任务详情
     */
    @GetMapping("/tasks/evidence/{taskId}")
    public ResponseDTO<TaskResponse> getEvidence(@PathVariable String taskId,
                                                 HttpServletRequest httpRequest) {
        log.info("[Agent] 查询任务证据: taskId={}", taskId);
        return ResponseDTO.success(taskService.get(
                taskId, requestUserResolver.requireUserScope(httpRequest)));
    }

    /**
     * 取消当前用户的 Agent 任务。
     *
     * @param taskId 任务标识
     * @param httpRequest HTTP 请求
     * @return 空业务数据
     */
    @PostMapping("/tasks/cancel/{taskId}")
    public ResponseDTO<Void> cancel(@PathVariable String taskId,
                                    HttpServletRequest httpRequest) {
        log.info("[Agent] 取消任务: taskId={}", taskId);
        taskService.cancel(taskId, requestUserResolver.requireUserScope(httpRequest));
        return ResponseDTO.success(null);
    }
}
// AI_GENERATE_END----
