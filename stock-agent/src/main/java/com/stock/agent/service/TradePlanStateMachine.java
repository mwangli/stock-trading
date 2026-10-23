// AI_GENERATE_START-----
package com.stock.agent.service;

import com.stock.agent.domain.AgentEnums;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * TradePlan 状态转换校验器。
 * 统一限制交易计划草案只能按照人工审批流程进入下一个状态。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Component
public class TradePlanStateMachine {

    private static final Map<AgentEnums.TradePlanStatus, Set<AgentEnums.TradePlanStatus>> TRANSITIONS = Map.of(
            AgentEnums.TradePlanStatus.PENDING_APPROVAL,
            EnumSet.of(AgentEnums.TradePlanStatus.APPROVED,
                    AgentEnums.TradePlanStatus.REJECTED,
                    AgentEnums.TradePlanStatus.EXPIRED),
            AgentEnums.TradePlanStatus.APPROVED, Set.of(),
            AgentEnums.TradePlanStatus.REJECTED, Set.of(),
            AgentEnums.TradePlanStatus.EXPIRED, Set.of());

    /**
     * 校验状态转换是否合法。
     *
     * @param current 当前状态
     * @param target 目标状态
     */
    public void requireTransition(AgentEnums.TradePlanStatus current,
                                  AgentEnums.TradePlanStatus target) {
        if (current == null || target == null
                || !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException("非法交易计划状态转换: " + current + " -> " + target);
        }
    }
}
// AI_GENERATE_END-----
