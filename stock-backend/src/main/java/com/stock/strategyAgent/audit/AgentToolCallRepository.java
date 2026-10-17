// AI_GENERATE_START -
package com.stock.strategyAgent.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Agent 工具调用审计仓储。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Repository
public interface AgentToolCallRepository extends JpaRepository<AgentToolCallEntity, Long> {
}
// AI_GENERATE_END -
