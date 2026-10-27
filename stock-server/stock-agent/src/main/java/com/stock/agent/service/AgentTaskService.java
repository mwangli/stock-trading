// AI_GENERATE_START---------
package com.stock.agent.service;

import com.stock.agent.enums.AgentTaskType;

import com.stock.agent.enums.AgentTaskStatus;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.dto.TaskResponse;
import com.stock.agent.entity.AgentSessionEntity;
import com.stock.agent.entity.AgentTaskEntity;
import com.stock.agent.repository.AgentTaskRepository;
import com.stock.agent.model.AgentRuntimeResponse;
import com.stock.agent.enums.AgentRuntimeStatus;
import com.stock.agent.output.AgentStructuredOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agent 任务状态与持久化服务。
 *
 * 统一维护任务创建、完成、失败、取消、归属校验和响应映射。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskService {

    private static final int MAX_SESSION_TASK_HISTORY = 200;

    private final AgentTaskRepository taskRepository;
    private final AgentSessionService sessionService;
    private final AgentProperties properties;

    /**
     * 创建运行中的 Agent 任务。
     *
     * @param session 所属会话
     * @param type 任务类型
     * @param question 用户问题或任务描述
     * @return 已持久化任务
     */
    public AgentTaskEntity create(AgentSessionEntity session, AgentTaskType type, String question) {
        LocalDateTime now = LocalDateTime.now();
        AgentTaskEntity task = taskRepository.save(new AgentTaskEntity()
                .setTaskId(UUID.randomUUID().toString())
                .setSessionId(session.getSessionId())
                .setTaskType(type)
                .setStatus(AgentTaskStatus.RUNNING)
                .setQuestion(question)
                .setCreatedAt(now)
                .setUpdatedAt(now));
        sessionService.updateTitleFromQuestion(session, question);
        sessionService.touch(session.getSessionId());
        log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=1/5 stage=TASK_CREATED questionLength={}",
                task.getTaskId(), task.getSessionId(), task.getTaskType(), question == null ? 0 : question.length());
        return task;
    }

    /**
     * 完成任务并保存结构化结果。
     *
     * @param task 待完成任务
     * @param response Runtime 响应
     * @return 任务响应
     */
    public TaskResponse complete(AgentTaskEntity task, AgentRuntimeResponse response) {
        AgentTaskStatus status;
        if (response.getStatus() == AgentRuntimeStatus.FAILED) {
            status = AgentTaskStatus.FAILED;
        } else if (response.getStatus() == AgentRuntimeStatus.DEGRADED) {
            status = AgentTaskStatus.DEGRADED;
        } else {
            status = AgentTaskStatus.COMPLETED;
        }
        AgentStructuredOutput structured = response.getStructuredOutput();
        String resultSummary = structured == null ? response.getContent() : structured.getSummary();
        String evidenceRefs = structured == null || structured.getEvidenceRefs() == null
                ? null : JSON.toJSONString(structured.getEvidenceRefs());
        task.setStatus(status)
                .setResultType(task.getTaskType().name())
                .setResultSummary(limit(resultSummary))
                .setStructuredResult(structured == null ? null : limit(JSON.toJSONString(structured)))
                .setEvidenceSummary(limit(response.getEvidenceSummary()))
                .setEvidenceRefs(evidenceRefs)
                .setUncertainty(structured == null ? null : limit(structured.getUncertainty()))
                .setErrorMessage(response.getErrorCode())
                .setUpdatedAt(LocalDateTime.now());
        log.info("[Agent][Progress] taskId={} sessionId={} type={} progress=5/5 stage=TASK_FINISHED status={} provider={} model={} errorCode={} elapsedMs={}",
                task.getTaskId(), task.getSessionId(), task.getTaskType(), task.getStatus(), response.getProvider(),
                response.getModel(), response.getErrorCode(), elapsedMillis(task));
        TaskResponse result = toResponse(taskRepository.save(task));
        sessionService.touch(task.getSessionId());
        return result;
    }

    /**
     * 标记任务失败并保存脱敏错误摘要。
     *
     * @param task 失败任务
     * @param exception 失败异常
     * @return 任务响应
     */
    public TaskResponse fail(AgentTaskEntity task, Exception exception) {
        task.setStatus(AgentTaskStatus.FAILED)
                .setErrorMessage(limit(exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage()))
                .setUpdatedAt(LocalDateTime.now());
        log.warn("[Agent][Progress] taskId={} sessionId={} type={} stage=TASK_FAILED status={} causeType={} causeMessage={} elapsedMs={}",
                task.getTaskId(), task.getSessionId(), task.getTaskType(), task.getStatus(),
                exception.getClass().getSimpleName(), safeErrorMessage(exception.getMessage()), elapsedMillis(task));
        TaskResponse result = toResponse(taskRepository.save(task));
        sessionService.touch(task.getSessionId());
        return result;
    }

    /**
     * 查询当前用户拥有的任务。
     *
     * @param taskId 任务标识
     * @param userScope 服务端认证用户范围
     * @return 任务响应
     */
    public TaskResponse get(String taskId, String userScope) {
        AgentTaskEntity task = requireOwned(taskId, userScope);
        return toResponse(task);
    }

    /**
     * 查询当前用户指定会话中的最近任务。
     *
     * @param sessionId 会话标识
     * @param userScope 服务端认证用户范围
     * @return 按创建时间正序排列的最近 200 个任务
     */
    public List<TaskResponse> listBySession(String sessionId, String userScope) {
        sessionService.requireOwned(sessionId, userScope);
        List<AgentTaskEntity> tasks = new ArrayList<>(
                taskRepository.findBySessionIdOrderByCreatedAtDesc(
                        sessionId.trim(), PageRequest.of(0, MAX_SESSION_TASK_HISTORY)));
        Collections.reverse(tasks);
        return tasks.stream().map(this::toResponse).toList();
    }

    /**
     * 取消当前用户尚未结束的任务。
     *
     * @param taskId 任务标识
     * @param userScope 服务端认证用户范围
     */
    public void cancel(String taskId, String userScope) {
        AgentTaskEntity task = requireOwned(taskId, userScope);
        if (task.getStatus() == AgentTaskStatus.COMPLETED
                || task.getStatus() == AgentTaskStatus.FAILED
                || task.getStatus() == AgentTaskStatus.CANCELLED) {
            throw new IllegalStateException("任务已结束，不能取消: " + taskId);
        }
        task.setStatus(AgentTaskStatus.CANCELLED).setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);
        sessionService.touch(task.getSessionId());
    }

    private AgentTaskEntity requireOwned(String taskId, String userScope) {
        AgentTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 任务不存在"));
        sessionService.requireOwned(task.getSessionId(), userScope);
        return task;
    }

    private TaskResponse toResponse(AgentTaskEntity entity) {
        return TaskResponse.builder()
                .taskId(entity.getTaskId())
                .sessionId(entity.getSessionId())
                .taskType(entity.getTaskType().name())
                .status(entity.getStatus().name())
                .question(entity.getQuestion())
                .resultSummary(entity.getResultSummary())
                .resultType(entity.getResultType())
                .structuredResult(entity.getStructuredResult())
                .evidenceSummary(entity.getEvidenceSummary())
                .evidenceRefs(entity.getEvidenceRefs())
                .uncertainty(entity.getUncertainty())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private long elapsedMillis(AgentTaskEntity task) {
        if (task.getCreatedAt() == null || task.getUpdatedAt() == null) {
            return -1L;
        }
        return Duration.between(task.getCreatedAt(), task.getUpdatedAt()).toMillis();
    }

    private String safeErrorMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String sanitized = message
                .replaceAll("(?i)(authorization\\s*:\\s*bearer\\s+|api[-_ ]?key\\s*[=:]\\s*)[^\\s,;]+", "$1***")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "sk-***");
        return sanitized.substring(0, Math.min(sanitized.length(), 300));
    }

    private String limit(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > properties.getMaxOutputChars()
                ? value.substring(0, properties.getMaxOutputChars()) : value;
    }
}
// AI_GENERATE_END--------