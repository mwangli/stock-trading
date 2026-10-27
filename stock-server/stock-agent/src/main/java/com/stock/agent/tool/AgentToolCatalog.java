// AI_GENERATE_START-----------------
package com.stock.agent.tool;

import com.stock.agent.enums.AgentToolName;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Agent 只读工具目录。
 * 工具对象由 Spring 管理，目录只负责按服务端允许名称选择工具，不执行字符串反射分发。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
public class AgentToolCatalog {

    private final TradingCalendarQueryTool tradingCalendarQueryTool;
    private final LatestPriceQueryTool latestPriceQueryTool;
    private final PredictionCandidateQueryTool predictionCandidateQueryTool;
    private final ModelOverviewQueryTool modelOverviewQueryTool;
    private final PerformanceOverviewQueryTool performanceOverviewQueryTool;
    private final BrokerStatusQueryTool brokerStatusQueryTool;
    private final BrokerFactQueryTool brokerFactQueryTool;
    private final JobDiagnosticTool jobDiagnosticTool;
    private final ProviderRuntimeQueryTool providerRuntimeQueryTool;
    private final ProjectDocumentSearchTool projectDocumentSearchTool;
    private final WebSearchTool webSearchTool;
    private final KnowledgeSearchTool knowledgeSearchTool;
    private final MarketAdvancersQueryTool marketAdvancersQueryTool;
    private final PerformanceAnalysisQueryTool performanceAnalysisQueryTool;
    private final StockDirectoryQueryTool stockDirectoryQueryTool;
    private final MarketDataQualityQueryTool marketDataQualityQueryTool;
    private final ModelOperationsQueryTool modelOperationsQueryTool;
    private final JobOperationsQueryTool jobOperationsQueryTool;
    private final BrokerHistoryQueryTool brokerHistoryQueryTool;

    /**
     * 创建首期只读工具目录。
     *
     * @param tradingCalendarQueryTool 交易日历工具
     * @param latestPriceQueryTool 最新价格工具
     * @param predictionCandidateQueryTool 候选查询工具
     * @param modelOverviewQueryTool 模型概览工具
     * @param performanceOverviewQueryTool 收益概览工具
     * @param brokerStatusQueryTool 券商状态工具
     * @param brokerFactQueryTool 券商账户事实工具
     * @param jobDiagnosticTool 任务诊断工具
     * @param providerRuntimeQueryTool Provider 运行状态工具
     * @param projectDocumentSearchTool 项目文档检索工具
     * @param webSearchTool 公开网页搜索工具
     * @param knowledgeSearchTool 用户范围内业务知识检索工具
     * @param marketAdvancersQueryTool 市场涨跌统计工具
     * @param performanceAnalysisQueryTool 月度收益分析工具
     * @param stockDirectoryQueryTool 股票目录工具
     * @param marketDataQualityQueryTool 市场数据质量工具
     * @param modelOperationsQueryTool 模型运维工具
     * @param jobOperationsQueryTool 调度任务运维工具
     * @param brokerHistoryQueryTool 券商历史工具
     */
    public AgentToolCatalog(TradingCalendarQueryTool tradingCalendarQueryTool,
                            LatestPriceQueryTool latestPriceQueryTool,
                            PredictionCandidateQueryTool predictionCandidateQueryTool,
                            ModelOverviewQueryTool modelOverviewQueryTool,
                            PerformanceOverviewQueryTool performanceOverviewQueryTool,
                            BrokerStatusQueryTool brokerStatusQueryTool,
                            BrokerFactQueryTool brokerFactQueryTool,
                            JobDiagnosticTool jobDiagnosticTool,
                            ProviderRuntimeQueryTool providerRuntimeQueryTool,
                            ProjectDocumentSearchTool projectDocumentSearchTool,
                            WebSearchTool webSearchTool,
                            KnowledgeSearchTool knowledgeSearchTool,
                            MarketAdvancersQueryTool marketAdvancersQueryTool,
                            PerformanceAnalysisQueryTool performanceAnalysisQueryTool,
                            StockDirectoryQueryTool stockDirectoryQueryTool,
                            MarketDataQualityQueryTool marketDataQualityQueryTool,
                            ModelOperationsQueryTool modelOperationsQueryTool,
                            JobOperationsQueryTool jobOperationsQueryTool,
                            BrokerHistoryQueryTool brokerHistoryQueryTool) {
        this.tradingCalendarQueryTool = tradingCalendarQueryTool;
        this.latestPriceQueryTool = latestPriceQueryTool;
        this.predictionCandidateQueryTool = predictionCandidateQueryTool;
        this.modelOverviewQueryTool = modelOverviewQueryTool;
        this.performanceOverviewQueryTool = performanceOverviewQueryTool;
        this.brokerStatusQueryTool = brokerStatusQueryTool;
        this.brokerFactQueryTool = brokerFactQueryTool;
        this.jobDiagnosticTool = jobDiagnosticTool;
        this.providerRuntimeQueryTool = providerRuntimeQueryTool;
        this.projectDocumentSearchTool = projectDocumentSearchTool;
        this.webSearchTool = webSearchTool;
        this.knowledgeSearchTool = knowledgeSearchTool;
        this.marketAdvancersQueryTool = marketAdvancersQueryTool;
        this.performanceAnalysisQueryTool = performanceAnalysisQueryTool;
        this.stockDirectoryQueryTool = stockDirectoryQueryTool;
        this.marketDataQualityQueryTool = marketDataQualityQueryTool;
        this.modelOperationsQueryTool = modelOperationsQueryTool;
        this.jobOperationsQueryTool = jobOperationsQueryTool;
        this.brokerHistoryQueryTool = brokerHistoryQueryTool;
    }

    /**
     * 返回本轮允许绑定到 AiServices 的工具对象。
     *
     * @param allowedTools 服务端能力路由允许的工具能力
     * @return 工具对象列表
     */
    public List<Object> resolve(Set<AgentToolName> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return List.of();
        }
        return allowedTools.stream().map(this::resolve).toList();
    }

    private Object resolve(AgentToolName toolName) {
        return switch (toolName) {
            case PROJECT_DOCUMENT_SEARCH -> projectDocumentSearchTool;
            case WEB_SEARCH -> webSearchTool;
            case KNOWLEDGE_SEARCH -> knowledgeSearchTool;
            case MARKET_ADVANCERS -> marketAdvancersQueryTool;
            case PERFORMANCE_ANALYSIS -> performanceAnalysisQueryTool;
            case TRADING_CALENDAR -> tradingCalendarQueryTool;
            case LATEST_PRICE -> latestPriceQueryTool;
            case PREDICTION_CANDIDATES -> predictionCandidateQueryTool;
            case MODEL_OVERVIEW -> modelOverviewQueryTool;
            case PERFORMANCE_OVERVIEW -> performanceOverviewQueryTool;
            case BROKER_STATUS -> brokerStatusQueryTool;
            case BROKER_FACTS -> brokerFactQueryTool;
            case JOB_DIAGNOSTIC -> jobDiagnosticTool;
            case PROVIDER_RUNTIME -> providerRuntimeQueryTool;
            case STOCK_DIRECTORY -> stockDirectoryQueryTool;
            case MARKET_DATA_QUALITY -> marketDataQualityQueryTool;
            case MODEL_OPERATIONS -> modelOperationsQueryTool;
            case JOB_OPERATIONS -> jobOperationsQueryTool;
            case BROKER_HISTORY -> brokerHistoryQueryTool;
        };
    }
}
// AI_GENERATE_END----------------
