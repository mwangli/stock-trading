// AI_GENERATE_START ---
package com.stock.agent.enums;

/**
 * Agent 交互任务生命周期状态。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public enum AgentTaskStatus {
    CREATED, RUNNING, WAITING_MODEL, WAITING_TOOL, COMPLETED, DEGRADED, FAILED, NEEDS_REVIEW, CANCELLED, EXPIRED
}
// AI_GENERATE_END --