// AI_GENERATE_START-------
package com.stock.agent.service;

import com.stock.agent.enums.TradePlanStatus;

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

    private static final Map<TradePlanStatus, Set<TradePlanStatus>> TRANSITIONS = Map.of(
            TradePlanStatus.PENDING_APPROVAL,
            EnumSet.of(TradePlanStatus.APPROVED,
                    TradePlanStatus.REJECTED,
                    TradePlanStatus.EXPIRED),
            TradePlanStatus.APPROVED, Set.of(),
            TradePlanStatus.REJECTED, Set.of(),
            TradePlanStatus.EXPIRED, Set.of());

    /**
     * 校验状态转换是否合法。
     *
     * @param current 当前状态
     * @param target 目标状态
     */
    public void requireTransition(TradePlanStatus current,
                                  TradePlanStatus target) {
        if (current == null || target == null
                || !TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException("非法交易计划状态转换: " + current + " -> " + target);
        }
    }
}
// AI_GENERATE_END------