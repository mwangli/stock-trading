// AI_GENERATE_START --
package com.stock.strategyAgent.persistence;

import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.domain.entity.AgentTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Agent 任务数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentTaskRepository extends JpaRepository<AgentTaskEntity, String> {

    /**
     * 查询会话中的任务。
     *
     * @param sessionId 会话标识
     * @return 任务列表
     */
    List<AgentTaskEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    /**
     * 查询指定状态任务数量。
     *
     * @param status 任务状态
     * @return 数量
     */
    long countByStatus(AgentEnums.TaskStatus status);
}
// AI_GENERATE_END --
