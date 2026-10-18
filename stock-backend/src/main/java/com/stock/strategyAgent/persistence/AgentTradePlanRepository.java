// AI_GENERATE_START ----
package com.stock.strategyAgent.persistence;

import com.stock.strategyAgent.domain.entity.AgentTradePlanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * 原子抢占一个尚未复核且未过期的交易计划。
     *
     * @param planId 计划标识
     * @param current 当前状态
     * @param target 目标状态
     * @param now 当前时间
     * @param traceId 执行链路标识
     * @return 成功抢占的记录数
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AgentTradePlanEntity p set p.status = :target, p.version = p.version + 1, "
            + "p.updatedAt = :now, p.executionTraceId = :traceId, "
            + "p.decisionMessage = '已完成执行抢占，进入当前事实复核' "
            + "where p.planId = :planId and p.status = :current and p.expiresAt > :now")
    int claimForExecution(@Param("planId") String planId,
                          @Param("current") AgentEnums.TradePlanStatus current,
                          @Param("target") AgentEnums.TradePlanStatus target,
                          @Param("now") java.time.LocalDateTime now,
                          @Param("traceId") String traceId);
}
// AI_GENERATE_END ----
