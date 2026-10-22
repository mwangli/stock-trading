// AI_GENERATE_START ----
package com.stock.strategyAgent.persistence;

import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.domain.entity.AgentMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Agent Memory 元数据数据访问接口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentMemoryRepository extends JpaRepository<AgentMemoryEntity, Long> {

    /**
     * 按记忆标识和认证用户范围读取记忆。
     *
     * @param memoryId 记忆标识
     * @param userScope 服务端认证用户范围
     * @return 用户本人记忆
     */
    java.util.Optional<AgentMemoryEntity> findByMemoryIdAndUserScope(String memoryId, String userScope);

    /**
     * 查询用户范围内的有效记忆。
     *
     * @param userScope 用户范围
     * @param status 记忆状态
     * @return 记忆列表
     */
    List<AgentMemoryEntity> findByUserScopeAndStatusOrderByUpdatedAtDesc(String userScope,
                                                                           AgentEnums.MemoryStatus status);
}
// AI_GENERATE_END ----
