// AI_GENERATE_START-------
package com.stock.agent.tooling.audit.repository;

import com.stock.agent.tooling.audit.entity.AgentToolCallEntity;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Agent 工具调用审计仓储。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Repository
public interface AgentToolCallRepository extends MongoRepository<AgentToolCallEntity, Long> {

    /**
     * 查询任务内已成功完成的工具调用。
     *
     * @param taskId 任务标识
     * @param status 调用状态
     * @return 按调用顺序排列的审计记录
     */
    List<AgentToolCallEntity> findByTaskIdAndStatusOrderByStartedAtAsc(String taskId, String status);
}
// AI_GENERATE_END------
