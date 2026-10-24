// AI_GENERATE_START-------
package com.stock.agent.tooling.capability;

import com.stock.agent.interaction.dto.AgentTaskStrategy;

import java.util.Set;

/**
 * Agent 单轮能力路由结果。
 *
 * @author mwangli
 * @since 2026-10-22
 */
public record AgentCapabilitySelection(AgentTaskStrategy strategy, Set<AgentToolName> allowedTools) {
}
// AI_GENERATE_END------
