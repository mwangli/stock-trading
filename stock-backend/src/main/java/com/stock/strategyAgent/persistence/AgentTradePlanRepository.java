// AI_GENERATE_START --
package com.stock.strategyAgent.persistence;

import com.stock.strategyAgent.domain.entity.AgentTradePlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import com.stock.strategyAgent.domain.AgentEnums;

/**
 * Agent 交易计划仓储。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Repository
public interface AgentTradePlanRepository extends JpaRepository<AgentTradePlanEntity, String> {

    /**
     * 按幂等键查询交易计划。
     *
     * @param idempotencyKey 幂等键
     * @return 交易计划
     */
    Optional<AgentTradePlanEntity> findByIdempotencyKey(String idempotencyKey);

    /**
     * 查询待重新校验的交易计划。
     *
     * @param status 计划状态
     * @return 待处理计划
     */
    List<AgentTradePlanEntity> findByStatusOrderByCreatedAtAsc(AgentEnums.TradePlanStatus status);
}
// AI_GENERATE_END --
