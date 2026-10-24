// AI_GENERATE_START----
package com.stock.agent.interaction.dto;

/**
 * Agent 统一聊天任务策略。
 *
 * @author mwangli
 * @since 2026-10-22
 */
public enum AgentTaskStrategy {

    /** 普通连续对话。 */
    CONVERSATION,

    /** 确定性业务事实查询。 */
    FACT_QUERY,

    /** 多来源业务分析。 */
    ANALYSIS,

    /** 深度研究。 */
    RESEARCH,

    /** 运行状态诊断。 */
    DIAGNOSTIC
}
// AI_GENERATE_END---
