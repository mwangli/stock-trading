// AI_GENERATE_START --
package com.stock.strategyAgent.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent 工具调用审计仓储。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Repository
public interface AgentToolCallRepository extends JpaRepository<AgentToolCallEntity, Long> {

    /**
     * 查询任务内已成功完成的工具调用。
     *
     * @param taskId 任务标识
     * @param status 调用状态
     * @return 按调用顺序排列的审计记录
     */
    List<AgentToolCallEntity> findByTaskIdAndStatusOrderByStartedAtAsc(String taskId, String status);
}
// AI_GENERATE_END --
