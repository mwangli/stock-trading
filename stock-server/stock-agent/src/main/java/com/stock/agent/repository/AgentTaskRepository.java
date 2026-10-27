// AI_GENERATE_START----------
package com.stock.agent.repository;

import com.stock.agent.enums.AgentTaskStatus;

import com.stock.agent.entity.AgentTaskEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 任务数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentTaskRepository extends MongoRepository<AgentTaskEntity, String> {

    /**
     * 查询会话中的任务。
     *
     * @param sessionId 会话标识
     * @param pageable 分页与数量限制
     * @return 任务列表
     */
    List<AgentTaskEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    /**
     * 查询会话最早创建的任务。
     *
     * @param sessionId 会话标识
     * @return 最早创建的任务
     */
    Optional<AgentTaskEntity> findFirstBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 查询指定状态任务数量。
     *
     * @param status 任务状态
     * @return 数量
     */
    long countByStatus(AgentTaskStatus status);
}
// AI_GENERATE_END---------