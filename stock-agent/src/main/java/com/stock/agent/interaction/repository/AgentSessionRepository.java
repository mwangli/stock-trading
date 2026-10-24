// AI_GENERATE_START----------
package com.stock.agent.interaction.repository;

import com.stock.agent.interaction.entity.AgentSessionEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Agent 会话数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentSessionRepository extends MongoRepository<AgentSessionEntity, String> {

    /**
     * 按更新时间倒序查询当前用户的会话。
     *
     * @param userScope 服务端认证用户范围
     * @param pageable 分页与数量限制
     * @return 当前用户的会话列表
     */
    List<AgentSessionEntity> findByUserScopeOrderByUpdatedAtDesc(String userScope, Pageable pageable);

    /**
     * 按会话标识和认证用户范围查询会话。
     *
     * @param sessionId 会话标识
     * @param userScope 服务端认证用户范围
     * @return 用户本人会话
     */
    Optional<AgentSessionEntity> findBySessionIdAndUserScope(String sessionId, String userScope);
}
// AI_GENERATE_END---------
