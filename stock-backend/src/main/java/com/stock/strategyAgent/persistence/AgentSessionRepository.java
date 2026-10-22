// AI_GENERATE_START ---
package com.stock.strategyAgent.persistence;

import com.stock.strategyAgent.domain.entity.AgentSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Agent 会话数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentSessionRepository extends JpaRepository<AgentSessionEntity, String> {

    /**
     * 按会话标识和认证用户范围查询会话。
     *
     * @param sessionId 会话标识
     * @param userScope 服务端认证用户范围
     * @return 用户本人会话
     */
    Optional<AgentSessionEntity> findBySessionIdAndUserScope(String sessionId, String userScope);
}
// AI_GENERATE_END ---
