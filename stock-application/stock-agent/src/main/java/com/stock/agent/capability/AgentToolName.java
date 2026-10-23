// AI_GENERATE_START---
package com.stock.agent.capability;

/**
 * Agent 首期只读工具能力枚举。
 * 枚举值用于服务端能力路由，外部名称与 LangChain4j 的 {@code @Tool} 名称保持一致。
 *
 * @author mwangli
 * @since 2026-10-22
 */
public enum AgentToolName {

    /** 交易日历查询。 */
    TRADING_CALENDAR("queryTradingCalendar"),

    /** 最新价格查询。 */
    LATEST_PRICE("queryLatestPrice"),

    /** 策略状态查询。 */
    STRATEGY_STATE("queryStrategyState"),

    /** 预测候选查询。 */
    PREDICTION_CANDIDATES("queryPredictionCandidates"),

    /** 模型概览查询。 */
    MODEL_OVERVIEW("queryModelOverview"),

    /** 收益概览查询。 */
    PERFORMANCE_OVERVIEW("queryPerformanceOverview"),

    /** 券商状态查询。 */
    BROKER_STATUS("queryBrokerStatus"),

    /** 调度任务诊断。 */
    JOB_DIAGNOSTIC("queryJobDiagnostic");

    private final String toolName;

    AgentToolName(String toolName) {
        this.toolName = toolName;
    }

    /**
     * 返回 LangChain4j 工具名称。
     *
     * @return 工具名称
     */
    public String toolName() {
        return toolName;
    }
}
// AI_GENERATE_END---
