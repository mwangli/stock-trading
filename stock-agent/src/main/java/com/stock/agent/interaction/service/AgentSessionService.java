// AI_GENERATE_START------
package com.stock.agent.interaction.service;

import com.stock.agent.interaction.model.AgentSessionStatus;

import com.stock.agent.interaction.dto.CreateSessionRequest;
import com.stock.agent.interaction.dto.SessionResponse;
import com.stock.agent.interaction.entity.AgentSessionEntity;
import com.stock.agent.interaction.repository.AgentSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
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

    private final AgentSessionRepository sessionRepository;

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
                .map(this::toResponse)
                .toList();
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
// AI_GENERATE_END-----
