// AI_GENERATE_START ----
package com.stock.agent.integration.adapter;

import com.stock.agent.integration.client.BackendInternalApiClient;
import com.stock.agent.integration.dto.BackendPredictionCandidateResponse;
import com.stock.agent.runtime.api.FactSourceStatus;
import com.stock.agent.runtime.api.FactSourceStatusPort;
import com.stock.agent.tooling.model.BrokerFacts;
import com.stock.agent.tooling.model.BrokerStatusFact;
import com.stock.agent.tooling.model.JobDiagnosticFact;
import com.stock.agent.tooling.model.LatestPriceFact;
import com.stock.agent.tooling.model.ModelOverviewFact;
import com.stock.agent.tooling.model.PerformanceOverviewFact;
import com.stock.agent.tooling.model.MarketAdvancerItemFact;
import com.stock.agent.tooling.model.MarketAdvancersFact;
import com.stock.agent.tooling.model.PerformanceAnalysisFact;
import com.stock.agent.tooling.model.PerformanceAnalysisItemFact;
import com.stock.agent.tooling.model.PredictionCandidateFact;
import com.stock.agent.tooling.model.PredictionCandidateItemFact;
import com.stock.agent.tooling.model.StrategyStateFact;
import com.stock.agent.tooling.model.TradeExecutionGateFact;
import com.stock.agent.tooling.model.TradingCalendarFact;
import com.stock.agent.tooling.port.ToolFactQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 基于 Backend 内部 HTTP API 的工具事实适配器。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class BackendToolFactAdapter implements ToolFactQueryPort, FactSourceStatusPort {

    private final BackendInternalApiClient client;

    /** {@inheritDoc} */
    @Override
    public LatestPriceFact latestPrice(String stockCode) {
        var wire = client.latestPrice(stockCode);
        return new LatestPriceFact(wire.stockCode(), wire.stockName(), wire.tradeDate(),
                wire.openPrice(), wire.highPrice(), wire.lowPrice(), wire.closePrice(),
                wire.volume(), wire.amount(), wire.updatedAt());
    }

    /** {@inheritDoc} */
    @Override
    public TradingCalendarFact tradingCalendar(LocalDate tradeDate) {
        var wire = client.tradingCalendar(tradeDate);
        return new TradingCalendarFact(wire.tradeDate(), wire.tradingDay(), wire.confirmed(),
                wire.nextConfirmedTradingDay());
    }

    /** {@inheritDoc} */
    @Override
    public StrategyStateFact strategyState() {
        var wire = client.strategyState();
        return new StrategyStateFact(wire.enabled(), wire.currentMode(), wire.configVersion(),
                wire.autoSwitchEnabled(), wire.disabledIndicators());
    }

    /** {@inheritDoc} */
    @Override
    public PredictionCandidateFact predictionCandidates(String candidateBatchId) {
        return toPredictionCandidate(client.predictionCandidates(candidateBatchId));
    }

    /** {@inheritDoc} */
    @Override
    public PredictionCandidateFact latestPredictionCandidates(LocalDate targetTradeDate) {
        return toPredictionCandidate(client.latestPredictionCandidates(targetTradeDate));
    }

    /** {@inheritDoc} */
    @Override
    public BrokerStatusFact brokerStatus() {
        var wire = client.brokerStatus();
        return new BrokerStatusFact(wire.brokerName(), wire.tradingMode(), wire.authenticated(),
                wire.realWriteEnabled(), wire.liveWriteAllowed(), wire.asOf());
    }

    /** {@inheritDoc} */
    @Override
    public BrokerFacts brokerFacts() {
        var wire = client.brokerFacts();
        return new BrokerFacts(wire.availableCash(), wire.totalAssets(), wire.positionCount(),
                wire.todayOrderCount(), wire.todayFillCount(), wire.asOf());
    }

    /** {@inheritDoc} */
    @Override
    public PerformanceOverviewFact performanceOverview() {
        var wire = client.performanceOverview();
        return new PerformanceOverviewFact(wire.capturedAt(), wire.totalAssets(), wire.availableCash(),
                wire.marketValue(), wire.cumulativeProfit(), wire.cumulativeReturnRate(),
                wire.dailyProfit(), wire.dailyReturnRate(), wire.monthlyProfit(), wire.monthlyReturnRate(),
                wire.closedTradeCount(), wire.winRate(), wire.asOf());
    }

    /** {@inheritDoc} */
    @Override
    public MarketAdvancersFact marketAdvancers() {
        var wire = client.marketAdvancers();
        var items = wire.items().stream()
                .map(item -> new MarketAdvancerItemFact(
                        item.stockCode(), item.stockName(), item.price(), item.changePercent()))
                .toList();
        return new MarketAdvancersFact(
                wire.dataDate(), wire.asOf(), wire.totalCount(), wire.upCount(), wire.downCount(),
                wire.flatCount(), items, wire.availabilityReason());
    }

    /** {@inheritDoc} */
    @Override
    public PerformanceAnalysisFact performanceAnalysis(YearMonth yearMonth) {
        var wire = client.performanceAnalysis(yearMonth);
        return new PerformanceAnalysisFact(
                wire.yearMonth(), wire.asOf(), wire.grossProfit(), wire.netProfit(), wire.totalFees(),
                wire.returnRate(), wire.tradeCount(), wire.winningTradeCount(), wire.losingTradeCount(),
                wire.winRate(), wire.topGains().stream().map(this::toPerformanceItem).toList(),
                wire.topLosses().stream().map(this::toPerformanceItem).toList(),
                wire.availabilityReason());
    }

    /** {@inheritDoc} */
    @Override
    public ModelOverviewFact modelOverview() {
        var wire = client.modelOverview();
        return new ModelOverviewFact(wire.modelName(), wire.activeVersionId(), wire.activeVersion(),
                wire.activeVersionStatus(), wire.activeModelHealthy(), wire.trainingRunning(),
                wire.latestTrainingRunId(), wire.latestTrainingStatus(),
                wire.latestTrainingCreatedAt(), wire.asOf());
    }

    /** {@inheritDoc} */
    @Override
    public JobDiagnosticFact jobDiagnostic(String jobName) {
        var wire = client.jobDiagnostic(jobName);
        return new JobDiagnosticFact(wire.jobName(), wire.status(), wire.errorMessage());
    }

    /** {@inheritDoc} */
    @Override
    public TradeExecutionGateFact tradeExecutionGate() {
        var wire = client.tradeExecutionGate();
        return new TradeExecutionGateFact(wire.targetTradeDate(), wire.tradingDay(),
                wire.executableCandidateCount(), wire.brokerAuthenticated(), wire.realWriteEnabled(),
                wire.status(), wire.blockedReasons());
    }
    /** {@inheritDoc} */
    @Override public FactSourceStatus status() { return client.status(); }

    private PredictionCandidateFact toPredictionCandidate(BackendPredictionCandidateResponse wire) {
        var items = wire.items().stream()
                .map(item -> new PredictionCandidateItemFact(item.stockCode(), item.stockName(),
                        item.rank(), item.totalScore(), item.reason()))
                .toList();
        return new PredictionCandidateFact(wire.candidateBatchId(), wire.predictionBatchId(),
                wire.strategyVersion(), wire.targetTradeDate(), items, wire.availabilityReason());
    }

    private PerformanceAnalysisItemFact toPerformanceItem(
            com.stock.agent.integration.dto.BackendPerformanceAnalysisItemResponse item) {
        return new PerformanceAnalysisItemFact(
                item.stockCode(), item.stockName(), item.grossProfit(), item.netProfit(), item.fees(),
                item.returnRate(), item.sellTime());
    }
}
// AI_GENERATE_END ---
