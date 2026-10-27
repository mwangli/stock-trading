// AI_GENERATE_START-------------
package com.stock.agent.enums;

/**
 * Agent 首期只读工具能力枚举。
 * 枚举值用于服务端能力路由，外部名称与 LangChain4j 的 {@code @Tool} 名称保持一致。
 *
 * @author mwangli
 * @since 2026-10-22
 */
public enum AgentToolName {

    /** 项目文档检索。 */
    PROJECT_DOCUMENT_SEARCH("searchProjectDocuments"),

    /** 公开网页搜索。 */
    WEB_SEARCH("searchWeb"),

    /** 用户范围内的业务 RAG 检索。 */
    KNOWLEDGE_SEARCH("searchKnowledge"),

    /** 最近可用行情日涨幅榜。 */
    MARKET_ADVANCERS("queryMarketAdvancers"),

    /** 指定自然月收益分析。 */
    PERFORMANCE_ANALYSIS("queryPerformanceAnalysis"),

    /** 交易日历查询。 */
    TRADING_CALENDAR("queryTradingCalendar"),

    /** 最新价格查询。 */
    LATEST_PRICE("queryLatestPrice"),

    /** 预测候选查询。 */
    PREDICTION_CANDIDATES("queryPredictionCandidates"),

    /** 模型概览查询。 */
    MODEL_OVERVIEW("queryModelOverview"),

    /** 收益概览查询。 */
    PERFORMANCE_OVERVIEW("queryPerformanceOverview"),

    /** 券商状态查询。 */
    BROKER_STATUS("queryBrokerStatus"),

    /** 券商账户事实摘要。 */
    BROKER_FACTS("queryBrokerFacts"),

    /** 调度任务诊断。 */
    JOB_DIAGNOSTIC("queryJobDiagnostic"),

    /** Agent Provider 运行状态。 */
    PROVIDER_RUNTIME("queryProviderRuntime"),

    /** 股票目录与行情摘要。 */
    STOCK_DIRECTORY("queryStockDirectory"),

    /** 市场基础数据质量。 */
    MARKET_DATA_QUALITY("queryMarketDataQuality"),

    /** 模型版本、训练和预测运维。 */
    MODEL_OPERATIONS("queryModelOperations"),

    /** 调度任务清单与运行历史。 */
    JOB_OPERATIONS("queryJobOperations"),

    /** 券商历史委托与成交。 */
    BROKER_HISTORY("queryBrokerHistory");

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
// AI_GENERATE_END------------
