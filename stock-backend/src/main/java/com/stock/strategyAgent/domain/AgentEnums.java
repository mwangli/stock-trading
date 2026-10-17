// AI_GENERATE_START ---
package com.stock.strategyAgent.domain;

/**
 * Agent 领域状态枚举集合。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public final class AgentEnums {

    private AgentEnums() {
    }

    /** Agent 任务类型。 */
    public enum TaskType { RESEARCH, DIAGNOSTIC, REPORT, TRADE_PLAN }

    /** Agent 任务状态。 */
    public enum TaskStatus { CREATED, RUNNING, WAITING_MODEL, WAITING_TOOL, COMPLETED, DEGRADED, FAILED, NEEDS_REVIEW, CANCELLED, EXPIRED }

    /** 会话状态。 */
    public enum SessionStatus { ACTIVE, CLOSED, EXPIRED }

    /** Memory 类型。 */
    public enum MemoryType { SESSION, PREFERENCE, SEMANTIC, EPISODIC, TRADING }

    /** Memory 来源可信度类型。 */
    public enum MemorySourceType { USER_CONFIRMED, SYSTEM_FACT, MODEL_INFERRED, TRADE_RESULT }

    /** Memory 生命周期状态。 */
    public enum MemoryStatus { ACTIVE, CORRECTED, FORGOTTEN, EXPIRED }

    /** TradePlan 自动执行状态。 */
    public enum TradePlanStatus { PENDING_REVALIDATION, RISK_REJECTED, EXECUTING, EXECUTED, FAILED, EXPIRED }
}
// AI_GENERATE_END ---
