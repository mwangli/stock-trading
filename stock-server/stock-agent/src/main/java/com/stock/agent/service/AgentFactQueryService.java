// AI_GENERATE_START-----------
package com.stock.agent.service;

import com.stock.agent.model.FactSourceStatus;
import com.stock.agent.model.BrokerHistoryFact;
import com.stock.agent.model.BrokerFacts;
import com.stock.agent.model.BrokerStatusFact;
import com.stock.agent.model.JobDiagnosticFact;
import com.stock.agent.model.JobOperationsFact;
import com.stock.agent.model.LatestPriceFact;
import com.stock.agent.model.MarketDataQualityFact;
import com.stock.agent.model.ModelOverviewFact;
import com.stock.agent.model.ModelOperationsFact;
import com.stock.agent.model.PerformanceOverviewFact;
import com.stock.agent.model.MarketAdvancerItemFact;
import com.stock.agent.model.MarketAdvancersFact;
import com.stock.agent.model.PerformanceAnalysisFact;
import com.stock.agent.model.PerformanceAnalysisItemFact;
import com.stock.agent.model.PredictionCandidateFact;
import com.stock.agent.model.PredictionCandidateItemFact;
import com.stock.agent.model.StockDirectoryFact;
import com.stock.agent.model.TradingCalendarFact;
import com.stock.platform.dto.InternalPerformanceAnalysisItemResponse;
import com.stock.platform.dto.InternalPerformanceAnalysisResponse;
import com.stock.platform.dto.InternalPredictionCandidateResponse;
import com.stock.platform.service.InternalAgentFactsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 查询 Agent 工具所需的同 JVM 权威业务事实。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class AgentFactQueryService {

    private final InternalAgentFactsService factsService;

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public LatestPriceFact latestPrice(String stockCode) {
        var wire = factsService.latestPrice(stockCode);
        return new LatestPriceFact(wire.stockCode(), wire.stockName(), wire.tradeDate(),
                wire.openPrice(), wire.highPrice(), wire.lowPrice(), wire.closePrice(),
                wire.volume(), wire.amount(), wire.updatedAt());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public TradingCalendarFact tradingCalendar(LocalDate tradeDate) {
        var wire = factsService.tradingCalendar(tradeDate);
        return new TradingCalendarFact(wire.tradeDate(), wire.tradingDay(), wire.confirmed(),
                wire.nextConfirmedTradingDay());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public PredictionCandidateFact predictionCandidates(String predictionBatchId) {
        return toPredictionCandidate(factsService.predictionCandidates(predictionBatchId));
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public PredictionCandidateFact latestPredictionCandidates(LocalDate targetTradeDate) {
        return toPredictionCandidate(factsService.latestPredictionCandidates(targetTradeDate));
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public BrokerStatusFact brokerStatus() {
        var wire = factsService.brokerStatus();
        return new BrokerStatusFact(wire.brokerName(), wire.tradingMode(), wire.authenticated(),
                wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public BrokerFacts brokerFacts() {
        var wire = factsService.brokerFacts();
        return new BrokerFacts(wire.availableCash(), wire.totalAssets(), wire.positionCount(),
                wire.todayOrderCount(), wire.todayFillCount(), wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public PerformanceOverviewFact performanceOverview() {
        var wire = factsService.performanceOverview();
        return new PerformanceOverviewFact(wire.capturedAt(), wire.totalAssets(), wire.availableCash(),
                wire.marketValue(), wire.cumulativeProfit(), wire.cumulativeReturnRate(),
                wire.dailyProfit(), wire.dailyReturnRate(), wire.monthlyProfit(), wire.monthlyReturnRate(),
                wire.closedTradeCount(), wire.winRate(), wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public MarketAdvancersFact marketAdvancers() {
        var wire = factsService.marketAdvancers();
        var items = wire.items().stream()
                .map(item -> new MarketAdvancerItemFact(
                        item.stockCode(), item.stockName(), item.price(), item.changePercent()))
                .toList();
        return new MarketAdvancersFact(
                wire.dataDate(), wire.asOf(), wire.totalCount(), wire.upCount(), wire.downCount(),
                wire.flatCount(), items, wire.availabilityReason());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public PerformanceAnalysisFact performanceAnalysis(YearMonth yearMonth) {
        return toPerformanceAnalysisFact(factsService.performanceAnalysis(yearMonth));
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public PerformanceAnalysisFact latestPerformanceAnalysis() {
        return toPerformanceAnalysisFact(factsService.latestPerformanceAnalysis());
    }

    private PerformanceAnalysisFact toPerformanceAnalysisFact(
            InternalPerformanceAnalysisResponse wire) {
        return new PerformanceAnalysisFact(
                wire.yearMonth(), wire.asOf(), wire.grossProfit(), wire.netProfit(), wire.totalFees(),
                wire.returnRate(), wire.tradeCount(), wire.winningTradeCount(), wire.losingTradeCount(),
                wire.winRate(), wire.topGains().stream().map(this::toPerformanceItem).toList(),
                wire.topLosses().stream().map(this::toPerformanceItem).toList(),
                wire.availabilityReason());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public ModelOverviewFact modelOverview() {
        var wire = factsService.modelOverview();
        return new ModelOverviewFact(wire.modelName(), wire.activeVersionId(), wire.activeVersion(),
                wire.activeVersionStatus(), wire.activeModelHealthy(), wire.trainingRunning(),
                wire.latestTrainingRunId(), wire.latestTrainingStatus(),
                wire.latestTrainingCreatedAt(), wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public JobDiagnosticFact jobDiagnostic(String jobName) {
        var wire = factsService.jobDiagnostic(jobName);
        return new JobDiagnosticFact(wire.jobName(), wire.status(), wire.errorMessage());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public StockDirectoryFact stockDirectory(String keyword, int limit) {
        var wire = factsService.stockDirectory(keyword, limit);
        return new StockDirectoryFact(wire.keyword(), wire.total(), wire.items(),
                wire.asOf(), wire.availabilityReason());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public MarketDataQualityFact marketDataQuality() {
        var wire = factsService.marketDataQuality();
        return new MarketDataQualityFact(wire.totalRecords(), wire.marketDistribution(),
                wire.completeness(), wire.missingFields(), wire.valid(), wire.dataQuality(), wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public ModelOperationsFact modelOperations(String mode, String referenceId, int limit) {
        var wire = factsService.modelOperations(mode, referenceId, limit);
        return new ModelOperationsFact(wire.mode(), wire.total(), wire.items(), wire.summary(), wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public JobOperationsFact jobOperations(String mode, String jobName, int limit) {
        var wire = factsService.jobOperations(mode, jobName, limit);
        return new JobOperationsFact(wire.mode(), wire.total(), wire.items(), wire.summary(), wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public BrokerHistoryFact brokerHistory(String type, LocalDate startDate, LocalDate endDate, int limit) {
        var wire = factsService.brokerHistory(type, startDate, endDate, limit);
        return new BrokerHistoryFact(wire.type(), wire.startDate(), wire.endDate(),
                wire.total(), wire.items(), wire.asOf());
    }

    /** 查询并转换 Agent 工具所需的权威事实。 */
    public FactSourceStatus status() {
        try {
            factsService.tradingCalendar(LocalDate.now());
            return new FactSourceStatus(true, true, "AVAILABLE", "Backend 本地权威事实源可用");
        } catch (RuntimeException exception) {
            return new FactSourceStatus(true, false, "UNAVAILABLE", "Backend 本地权威事实源不可用");
        }
    }

    private PredictionCandidateFact toPredictionCandidate(InternalPredictionCandidateResponse wire) {
        var items = wire.items().stream()
                .map(item -> new PredictionCandidateItemFact(item.stockCode(), item.stockName(),
                        item.rank(), item.effectiveModelScore(), item.estimatedNetReturn()))
                .toList();
        return new PredictionCandidateFact(wire.predictionBatchId(), wire.modelVersion(),
                wire.targetTradeDate(), items, wire.availabilityReason());
    }

    private PerformanceAnalysisItemFact toPerformanceItem(
            InternalPerformanceAnalysisItemResponse item) {
        return new PerformanceAnalysisItemFact(
                item.stockCode(), item.stockName(), item.grossProfit(), item.netProfit(), item.fees(),
                item.returnRate(), item.sellTime());
    }
}
// AI_GENERATE_END-----------
