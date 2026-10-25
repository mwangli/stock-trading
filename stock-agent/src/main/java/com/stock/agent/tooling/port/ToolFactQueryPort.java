// AI_GENERATE_START ----
package com.stock.agent.tooling.port;

import com.stock.agent.tooling.model.BrokerFacts;
import com.stock.agent.tooling.model.BrokerStatusFact;
import com.stock.agent.tooling.model.JobDiagnosticFact;
import com.stock.agent.tooling.model.LatestPriceFact;
import com.stock.agent.tooling.model.ModelOverviewFact;
import com.stock.agent.tooling.model.PerformanceOverviewFact;
import com.stock.agent.tooling.model.MarketAdvancersFact;
import com.stock.agent.tooling.model.PerformanceAnalysisFact;
import com.stock.agent.tooling.model.PredictionCandidateFact;
import com.stock.agent.tooling.model.StrategyStateFact;
import com.stock.agent.tooling.model.TradeExecutionGateFact;
import com.stock.agent.tooling.model.TradingCalendarFact;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Agent 只读工具所需权威事实查询端口。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public interface ToolFactQueryPort {

    /** @param stockCode 股票代码 @return 最新价格事实 */
    LatestPriceFact latestPrice(String stockCode);

    /** @param tradeDate 查询日期 @return 交易日历事实 */
    TradingCalendarFact tradingCalendar(LocalDate tradeDate);

    /** @return 策略状态事实 */
    StrategyStateFact strategyState();

    /** @param candidateBatchId 候选批次标识 @return 候选批次事实 */
    PredictionCandidateFact predictionCandidates(String candidateBatchId);

    /** @param targetTradeDate 目标交易日 @return 最新候选批次事实 */
    PredictionCandidateFact latestPredictionCandidates(LocalDate targetTradeDate);

    /** @return 券商连接状态事实 */
    BrokerStatusFact brokerStatus();

    /** @return 脱敏券商账户事实 */
    BrokerFacts brokerFacts();

    /** @return 账户收益概览事实 */
    PerformanceOverviewFact performanceOverview();

    /** @return 最近可用行情日的涨跌分布事实 */
    MarketAdvancersFact marketAdvancers();

    /** @param yearMonth 查询自然月 @return 月度收益分析事实 */
    PerformanceAnalysisFact performanceAnalysis(YearMonth yearMonth);

    /** @return 最近一个存在已实现交易的自然月收益分析事实 */
    PerformanceAnalysisFact latestPerformanceAnalysis();

    /** @return 模型运维概览事实 */
    ModelOverviewFact modelOverview();

    /** @param jobName 任务名称 @return 任务诊断事实 */
    JobDiagnosticFact jobDiagnostic(String jobName);

    /** @return 交易执行门禁事实 */
    TradeExecutionGateFact tradeExecutionGate();
}
// AI_GENERATE_END ---
