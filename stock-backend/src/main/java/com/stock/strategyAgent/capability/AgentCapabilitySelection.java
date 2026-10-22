// AI_GENERATE_START ---
package com.stock.strategyAgent.capability;

import com.stock.strategyAgent.api.AgentTaskStrategy;

import java.util.Set;

/**
 * Agent 单轮能力路由结果。
 *
 * @author mwangli
 * @since 2026-10-22
 */
public record AgentCapabilitySelection(AgentTaskStrategy strategy, Set<AgentToolName> allowedTools) {
}
// AI_GENERATE_END ---
