// AI_GENERATE_START -
package com.stock.strategyAgent.runtime;

/**
 * Agent Runtime 执行状态。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public enum AgentRuntimeStatus {
    /** 模型正常返回。 */
    COMPLETED,
    /** 模型不可用，但可以返回结构化事实摘要。 */
    DEGRADED,
    /** 输入、模型或运行时执行失败。 */
    FAILED
}
// AI_GENERATE_END -
