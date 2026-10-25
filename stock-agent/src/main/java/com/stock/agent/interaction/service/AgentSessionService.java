// AI_GENERATE_START--------
package com.stock.agent.interaction.service;

import com.stock.agent.interaction.model.AgentSessionStatus;

import com.stock.agent.interaction.dto.CreateSessionRequest;
import com.stock.agent.interaction.dto.SessionResponse;
import com.stock.agent.interaction.entity.AgentSessionEntity;
import com.stock.agent.interaction.repository.AgentSessionRepository;
import com.stock.agent.interaction.repository.AgentTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agent 会话领域服务。
 *
 * 负责会话创建、用户归属校验和按需创建自动会话。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private static final int MAX_SESSION_HISTORY = 50;
    private static final int MAX_TITLE_LENGTH = 28;
    private static final Set<String> DEFAULT_TITLES = Set.of(
            "Agent 会话", "Agent 自动会话", "智能策略对话", "新对话");

    private final AgentSessionRepository sessionRepository;
    private final AgentTaskRepository taskRepository;

    /**
     * 创建当前用户的 Agent 会话。
     *
     * @param request 创建请求
     * @param userScope 服务端认证用户范围
     * @return 会话响应
     */
    public SessionResponse create(CreateSessionRequest request, String userScope) {
        LocalDateTime now = LocalDateTime.now();
        AgentSessionEntity entity = new AgentSessionEntity()
                .setSessionId(UUID.randomUUID().toString())
                .setUserScope(requireUserScope(userScope))
                .setTitle(request == null || !StringUtils.hasText(request.getTitle())
                        ? "Agent 会话" : request.getTitle().trim())
                .setStatus(AgentSessionStatus.ACTIVE)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        return toResponse(sessionRepository.save(entity));
    }

    /**
     * 查询当前用户拥有的 Agent 会话。
     *
     * @param sessionId 会话标识
     * @param userScope 服务端认证用户范围
     * @return 会话响应
     */
    public SessionResponse get(String sessionId, String userScope) {
        return toResponse(requireOwned(sessionId, userScope));
    }

    /**
     * 查询当前用户最近活动的 Agent 会话。
     *
     * @param userScope 服务端认证用户范围
     * @return 按最近更新时间倒序排列的最近 50 个会话
     */
    public List<SessionResponse> list(String userScope) {
        return sessionRepository.findByUserScopeOrderByUpdatedAtDesc(
                        requireUserScope(userScope), PageRequest.of(0, MAX_SESSION_HISTORY)).stream()
                .map(this::backfillTitleFromFirstQuestion)
                .map(this::toResponse)
                .toList();
    }

    /**
     * 使用首个用户问题更新默认会话标题。
     *
     * @param session 会话实体
     * @param question 用户问题
     */
    public void updateTitleFromQuestion(AgentSessionEntity session, String question) {
        if (session == null || !isDefaultTitle(session.getTitle())) {
            return;
        }
        String summarizedTitle = summarizeTitle(question);
        if (!StringUtils.hasText(summarizedTitle)) {
            return;
        }
        session.setTitle(summarizedTitle);
        sessionRepository.save(session);
    }

    /**
     * 刷新会话最近活动时间。
     *
     * @param sessionId 会话标识
     */
    public void touch(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    /**
     * 获取已有会话，未指定会话时为当前用户创建自动会话。
     *
     * @param sessionId 可选会话标识
     * @param userScope 服务端认证用户范围
     * @return 会话实体
     */
    public AgentSessionEntity ensure(String sessionId, String userScope) {
        if (StringUtils.hasText(sessionId)) {
            return requireOwned(sessionId, userScope);
        }
        CreateSessionRequest request = new CreateSessionRequest();
        request.setTitle("Agent 自动会话");
        SessionResponse response = create(request, userScope);
        return requireOwned(response.getSessionId(), userScope);
    }

    /**
     * 校验并返回当前用户拥有的会话。
     *
     * @param sessionId 会话标识
     * @param userScope 服务端认证用户范围
     * @return 会话实体
     */
    public AgentSessionEntity requireOwned(String sessionId, String userScope) {
        if (!StringUtils.hasText(sessionId)) {
            throw new IllegalArgumentException("Agent 会话不存在");
        }
        return sessionRepository.findBySessionIdAndUserScope(sessionId.trim(), requireUserScope(userScope))
                .orElseThrow(() -> new IllegalArgumentException("Agent 会话不存在"));
    }

    private String requireUserScope(String userScope) {
        if (!StringUtils.hasText(userScope)) {
            throw new IllegalArgumentException("Agent 用户范围不能为空");
        }
        return userScope.trim();
    }

    private AgentSessionEntity backfillTitleFromFirstQuestion(AgentSessionEntity session) {
        if (!isDefaultTitle(session.getTitle())) {
            return session;
        }
        taskRepository.findFirstBySessionIdOrderByCreatedAtAsc(session.getSessionId())
                .ifPresent(task -> updateTitleFromQuestion(session, task.getQuestion()));
        return session;
    }

    private boolean isDefaultTitle(String title) {
        return !StringUtils.hasText(title) || DEFAULT_TITLES.contains(title.trim());
    }

    private String summarizeTitle(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String normalized = question
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("(?m)^\\s{0,3}(?:#{1,6}|>|[-*+]\\s|\\d+[.)]\\s)\\s*", "")
                .replaceAll("[*_~]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        int sentenceEnd = firstSentenceEnd(normalized);
        String firstSentence = sentenceEnd < 0 ? normalized : normalized.substring(0, sentenceEnd + 1).trim();
        int[] codePoints = firstSentence.codePoints().toArray();
        if (codePoints.length <= MAX_TITLE_LENGTH) {
            return firstSentence;
        }
        return new String(codePoints, 0, MAX_TITLE_LENGTH) + "…";
    }

    private int firstSentenceEnd(String content) {
        int firstEnd = -1;
        for (char delimiter : new char[]{'。', '！', '？', '!', '?'}) {
            int index = content.indexOf(delimiter);
            if (index >= 0 && (firstEnd < 0 || index < firstEnd)) {
                firstEnd = index;
            }
        }
        return firstEnd;
    }

    private SessionResponse toResponse(AgentSessionEntity entity) {
        return SessionResponse.builder()
                .sessionId(entity.getSessionId())
                .title(entity.getTitle())
                .status(entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
// AI_GENERATE_END-------
