// AI_GENERATE_START--------
package com.stock.agent.tooling.capability;

import com.stock.agent.tooling.tool.read.BrokerStatusQueryTool;
import com.stock.agent.tooling.tool.read.JobDiagnosticTool;
import com.stock.agent.tooling.tool.read.LatestPriceQueryTool;
import com.stock.agent.tooling.tool.read.ModelOverviewQueryTool;
import com.stock.agent.tooling.tool.read.PerformanceOverviewQueryTool;
import com.stock.agent.tooling.tool.read.PredictionCandidateQueryTool;
import com.stock.agent.tooling.tool.read.StrategyStateQueryTool;
import com.stock.agent.tooling.tool.read.TradingCalendarQueryTool;
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
    private final StrategyStateQueryTool strategyStateQueryTool;
    private final PredictionCandidateQueryTool predictionCandidateQueryTool;
    private final ModelOverviewQueryTool modelOverviewQueryTool;
    private final PerformanceOverviewQueryTool performanceOverviewQueryTool;
    private final BrokerStatusQueryTool brokerStatusQueryTool;
    private final JobDiagnosticTool jobDiagnosticTool;

    /**
     * 创建首期只读工具目录。
     *
     * @param tradingCalendarQueryTool 交易日历工具
     * @param latestPriceQueryTool 最新价格工具
     * @param strategyStateQueryTool 策略状态工具
     * @param predictionCandidateQueryTool 候选查询工具
     * @param modelOverviewQueryTool 模型概览工具
     * @param performanceOverviewQueryTool 收益概览工具
     * @param brokerStatusQueryTool 券商状态工具
     * @param jobDiagnosticTool 任务诊断工具
     */
    public AgentToolCatalog(TradingCalendarQueryTool tradingCalendarQueryTool,
                            LatestPriceQueryTool latestPriceQueryTool,
                            StrategyStateQueryTool strategyStateQueryTool,
                            PredictionCandidateQueryTool predictionCandidateQueryTool,
                            ModelOverviewQueryTool modelOverviewQueryTool,
                            PerformanceOverviewQueryTool performanceOverviewQueryTool,
                            BrokerStatusQueryTool brokerStatusQueryTool,
                            JobDiagnosticTool jobDiagnosticTool) {
        this.tradingCalendarQueryTool = tradingCalendarQueryTool;
        this.latestPriceQueryTool = latestPriceQueryTool;
        this.strategyStateQueryTool = strategyStateQueryTool;
        this.predictionCandidateQueryTool = predictionCandidateQueryTool;
        this.modelOverviewQueryTool = modelOverviewQueryTool;
        this.performanceOverviewQueryTool = performanceOverviewQueryTool;
        this.brokerStatusQueryTool = brokerStatusQueryTool;
        this.jobDiagnosticTool = jobDiagnosticTool;
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
            case TRADING_CALENDAR -> tradingCalendarQueryTool;
            case LATEST_PRICE -> latestPriceQueryTool;
            case STRATEGY_STATE -> strategyStateQueryTool;
            case PREDICTION_CANDIDATES -> predictionCandidateQueryTool;
            case MODEL_OVERVIEW -> modelOverviewQueryTool;
            case PERFORMANCE_OVERVIEW -> performanceOverviewQueryTool;
            case BROKER_STATUS -> brokerStatusQueryTool;
            case JOB_DIAGNOSTIC -> jobDiagnosticTool;
        };
    }
}
// AI_GENERATE_END-------
