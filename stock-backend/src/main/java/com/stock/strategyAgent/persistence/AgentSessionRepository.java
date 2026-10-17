// AI_GENERATE_START --
package com.stock.strategyAgent.persistence;

import com.stock.strategyAgent.domain.entity.AgentSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Agent 会话数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentSessionRepository extends JpaRepository<AgentSessionEntity, String> {
}
// AI_GENERATE_END --
