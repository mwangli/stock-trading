// AI_GENERATE_START------
package com.stock.platform.service;

import com.stock.platform.dto.InternalBrokerFactsResponse;
import com.stock.platform.dto.InternalBrokerStatusResponse;
import com.stock.platform.dto.InternalJobDiagnosticResponse;
import com.stock.platform.dto.InternalLatestPriceResponse;
import com.stock.platform.dto.InternalModelOverviewResponse;
import com.stock.platform.dto.InternalPerformanceOverviewResponse;
import com.stock.platform.dto.InternalMarketAdvancerItemResponse;
import com.stock.platform.dto.InternalMarketAdvancersResponse;
import com.stock.platform.dto.InternalPerformanceAnalysisItemResponse;
import com.stock.platform.dto.InternalPerformanceAnalysisResponse;
import com.stock.platform.dto.InternalPredictionCandidateItem;
import com.stock.platform.dto.InternalPredictionCandidateResponse;
import com.stock.platform.dto.InternalStrategyStateResponse;
import com.stock.platform.dto.InternalTradeExecutionGateResponse;
import com.stock.platform.dto.InternalTradingCalendarResponse;
import com.stock.platform.port.JobExecutionQueryPort;
import com.stock.platform.dto.JobExecutionSnapshot;
import com.stock.market.dto.StockPriceItemDto;
import com.stock.market.service.MarketDataQueryService;
import com.stock.market.service.StockQueryService;
import com.stock.market.service.TradingCalendarService;
import com.stock.model.dto.ModelOverviewResponse;
import com.stock.model.dto.ModelTrainingRunResponse;
import com.stock.model.dto.ModelVersionResponse;
import com.stock.model.service.ModelOperationsQueryService;
import com.stock.decision.config.StrategyStateManager;
import com.stock.decision.dto.StrategyStateDto;
import com.stock.decision.algorithm.StockSelector;
import com.stock.decision.dto.StockRankingDto;
import com.stock.execution.dto.BrokerReadStatusDto;
import com.stock.execution.dto.PerformanceOverviewDto;
import com.stock.execution.dto.BrokerAccountDto;
import com.stock.execution.config.TradingProperties;
import com.stock.execution.service.BrokerReadService;
import com.stock.execution.service.PerformanceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 向 Agent 提供最小化只读业务事实。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class InternalAgentFactsService {

    private final MarketDataQueryService marketDataQueryService;
    private final StockQueryService stockQueryService;
    private final TradingCalendarService tradingCalendarService;
    private final StrategyStateManager strategyStateManager;
    private final BrokerReadService brokerReadService;
    private final PerformanceReportService performanceReportService;
    private final ModelOperationsQueryService modelOperationsQueryService;
    private final JobExecutionQueryPort jobExecutionQueryPort;
    private final StockSelector stockSelector;
    private final TradingProperties tradingProperties;

    /**
     * 查询股票最新日线事实。
     *
     * @param stockCode 股票代码
     * @return 最新日线事实
     */
    public InternalLatestPriceResponse latestPrice(String stockCode) {
        StockPriceItemDto price = marketDataQueryService.findRecentPrices(stockCode, null, 1).stream()
                .findFirst().orElse(null);
        if (price == null) {
            throw new IllegalArgumentException("未查询到股票最新价格: " + stockCode);
        }
        return new InternalLatestPriceResponse(price.getCode(), price.getStockName(), price.getDate(),
                price.getOpenPrice(), price.getHighPrice(), price.getLowPrice(), price.getClosePrice(),
                price.getVolume(), price.getAmount(), price.getUpdateTime());
    }

    /**
     * 查询交易日历事实。
     *
     * @param tradeDate 查询日期
     * @return 交易日历事实
     */
    public InternalTradingCalendarResponse tradingCalendar(LocalDate tradeDate) {
        boolean tradingDay = tradingCalendarService.isTradingDay(tradeDate);
        boolean confirmed = tradingCalendarService.isConfirmedDate(tradeDate);
        LocalDate nextConfirmedTradingDay = tradingDay
                ? tradeDate
                : tradingCalendarService.findNextTradingDay(tradeDate).orElse(null);
        return new InternalTradingCalendarResponse(
                tradeDate, tradingDay, confirmed, nextConfirmedTradingDay);
    }

    /**
     * 查询当前策略状态。
     *
     * @return 策略状态
     */
    public InternalStrategyStateResponse strategyState() {
        StrategyStateDto state = strategyStateManager.getCurrentState();
        return new InternalStrategyStateResponse(state.isEnabled(), state.getCurrentMode().name(),
                state.getConfigVersion(), state.isAutoSwitchEnabled(), state.getDisabledIndicators());
    }

    /**
     * 查询指定候选批次摘要。
     *
     * @param candidateBatchId 候选批次标识
     * @return 候选批次摘要
     */
    public InternalPredictionCandidateResponse predictionCandidates(String candidateBatchId) {
        List<StockRankingDto> rankings = stockSelector.getRankingsByCandidateBatchId(candidateBatchId);
        if (rankings.isEmpty()) {
            return emptyCandidates(candidateBatchId, null,
                    "指定候选批次没有排名记录，无法确认候选排序；请检查候选生成任务、目标交易日和批次是否已过期");
        }
        StockRankingDto first = rankings.get(0);
        List<InternalPredictionCandidateItem> items = rankings.stream().limit(20)
                .map(item -> new InternalPredictionCandidateItem(item.getStockCode(), item.getStockName(),
                        item.getRank(), item.getTotalScore(), item.getReason()))
                .toList();
        return new InternalPredictionCandidateResponse(first.getCandidateBatchId(), first.getPredictionBatchId(),
                first.getStrategyConfigVersion(), first.getTargetTradeDate(), items,
                "已读取候选排名，共 " + rankings.size() + " 条，展示前 " + items.size() + " 条");
    }

    /**
     * 查询目标交易日的最新候选批次摘要。
     *
     * @param targetTradeDate 目标交易日
     * @return 最新候选批次摘要
     */
    public InternalPredictionCandidateResponse latestPredictionCandidates(LocalDate targetTradeDate) {
        String candidateBatchId = stockSelector.getLatestCandidateBatchId(targetTradeDate);
        return candidateBatchId == null
                ? emptyCandidates(null, targetTradeDate,
                "目标交易日没有候选排名快照，不能判断候选排序或生成买入计划")
                : predictionCandidates(candidateBatchId);
    }

    /**
     * 查询券商只读状态。
     *
     * @return 券商状态
     */
    public InternalBrokerStatusResponse brokerStatus() {
        BrokerReadStatusDto status = brokerReadService.getStatus();
        return new InternalBrokerStatusResponse(status.getBrokerName(), status.getTradingMode(),
                status.isAuthenticated(), status.isRealWriteEnabled(), status.isLiveWriteAllowed(),
                LocalDateTime.now());
    }

    /**
     * 查询账户、持仓、订单和成交数量摘要。
     *
     * @return 券商事实摘要
     */
    public InternalBrokerFactsResponse brokerFacts() {
        BrokerAccountDto account = brokerReadService.getAccount();
        int positionCount = brokerReadService.getPositionCount();
        int orderCount = brokerReadService.getTodayOrders().getTotal();
        int fillCount = brokerReadService.getTodayFills().getTotal();
        return new InternalBrokerFactsResponse(account == null ? null : account.getAvailableCash(),
                account == null ? null : account.getTotalAssets(), positionCount, orderCount,
                fillCount, LocalDateTime.now());
    }

    /**
     * 查询收益概览。
     *
     * @return 收益概览
     */
    public InternalPerformanceOverviewResponse performanceOverview() {
        PerformanceOverviewDto overview = performanceReportService.getOverview();
        return new InternalPerformanceOverviewResponse(overview.getCapturedAt(), overview.getTotalAssets(),
                overview.getAvailableCash(), overview.getMarketValue(), overview.getCumulativeProfit(),
                overview.getCumulativeReturnRate(), overview.getDailyProfit(), overview.getDailyReturnRate(),
                overview.getMonthlyProfit(), overview.getMonthlyReturnRate(), overview.getClosedTradeCount(),
                overview.getWinRate(), LocalDateTime.now());
    }

    /**
     * 查询最近可用行情日的涨跌分布。
     *
     * @return 市场涨幅事实
     */
    public InternalMarketAdvancersResponse marketAdvancers() {
        var result = stockQueryService.getMarketAdvancers(20);
        var items = result.getItems().stream()
                .map(item -> new InternalMarketAdvancerItemResponse(
                        item.getStockCode(), item.getStockName(), item.getPrice(), item.getChangePercent()))
                .toList();
        return new InternalMarketAdvancersResponse(
                result.getDataDate(), result.getAsOf(), result.getTotalCount(), result.getUpCount(),
                result.getDownCount(), result.getFlatCount(), items, result.getAvailabilityReason());
    }

    /**
     * 查询指定自然月的已实现收益分析。
     *
     * @param yearMonth 查询自然月
     * @return 月度收益事实
     */
    public InternalPerformanceAnalysisResponse performanceAnalysis(YearMonth yearMonth) {
        return toPerformanceAnalysisResponse(performanceReportService.getAnalysis(yearMonth));
    }

    /**
     * 查询最近一个存在已实现交易的自然月收益分析。
     *
     * @return 最近有交易月份的收益事实
     */
    public InternalPerformanceAnalysisResponse latestPerformanceAnalysis() {
        return toPerformanceAnalysisResponse(performanceReportService.getLatestAnalysisWithTrades());
    }

    private InternalPerformanceAnalysisResponse toPerformanceAnalysisResponse(
            com.stock.execution.dto.PerformanceAnalysisDto result) {
        return new InternalPerformanceAnalysisResponse(
                result.getYearMonth(), result.getAsOf(), result.getGrossProfit(), result.getNetProfit(),
                result.getTotalFees(), result.getReturnRate(), result.getTradeCount(),
                result.getWinningTradeCount(), result.getLosingTradeCount(), result.getWinRate(),
                result.getTopGains().stream().map(this::toPerformanceAnalysisItem).toList(),
                result.getTopLosses().stream().map(this::toPerformanceAnalysisItem).toList(),
                result.getAvailabilityReason());
    }

    private InternalPerformanceAnalysisItemResponse toPerformanceAnalysisItem(
            com.stock.execution.dto.PerformanceAnalysisItemDto item) {
        return new InternalPerformanceAnalysisItemResponse(
                item.getStockCode(), item.getStockName(), item.getGrossProfit(), item.getNetProfit(),
                item.getFees(), item.getReturnRate(), item.getSellTime());
    }

    /**
     * 查询模型运维概览。
     *
     * @return 模型运维概览
     */
    public InternalModelOverviewResponse modelOverview() {
        ModelOverviewResponse overview = modelOperationsQueryService.getOverview();
        ModelVersionResponse active = overview.getActiveVersion();
        ModelTrainingRunResponse training = overview.getLatestTrainingRun();
        return new InternalModelOverviewResponse(overview.getModelName(),
                active == null ? null : active.getId(),
                active == null ? null : active.getModelVersion(),
                active == null ? null : active.getStatus(),
                overview.isActiveModelHealthy(), overview.isTrainingRunning(),
                training == null ? null : training.getId(),
                training == null ? null : training.getStatus(),
                training == null ? null : training.getCreatedAt(), LocalDateTime.now());
    }

    /**
     * 查询任务最近运行摘要。
     *
     * @param jobName 任务名称
     * @return 任务诊断摘要
     */
    public InternalJobDiagnosticResponse jobDiagnostic(String jobName) {
        JobExecutionSnapshot snapshot = jobExecutionQueryPort.findLatest(jobName);
        return snapshot == null
                ? new InternalJobDiagnosticResponse(jobName, "UNKNOWN", "没有运行记录")
                : new InternalJobDiagnosticResponse(jobName, snapshot.status(), snapshot.errorMessage());
    }

    /**
     * 查询当前真实交易执行门禁摘要。
     *
     * @return 执行门禁摘要
     */
    public InternalTradeExecutionGateResponse tradeExecutionGate() {
        LocalDate targetTradeDate = stockSelector.getCurrentTargetTradeDate();
        boolean tradingDay = tradingCalendarService.isTradingDay(targetTradeDate);
        boolean brokerAuthenticated = brokerReadService.getStatus().isAuthenticated();
        boolean realWriteEnabled = tradingProperties.isLiveWriteAllowed();
        int candidateCount = stockSelector.getExecutableRankings(targetTradeDate).size();
        List<String> blockedReasons = new ArrayList<>();
        if (!tradingDay) {
            blockedReasons.add("目标交易日不是已确认交易日");
        }
        if (candidateCount == 0) {
            blockedReasons.add("当前目标交易日没有可执行候选");
        }
        if (!brokerAuthenticated) {
            blockedReasons.add("券商会话未认证");
        }
        if (!realWriteEnabled) {
            blockedReasons.add("真实交易写入总门禁未开启");
        }
        return new InternalTradeExecutionGateResponse(targetTradeDate, tradingDay, candidateCount,
                brokerAuthenticated, realWriteEnabled, blockedReasons.isEmpty() ? "READY" : "BLOCKED",
                List.copyOf(blockedReasons));
    }

    private InternalPredictionCandidateResponse emptyCandidates(
            String candidateBatchId, LocalDate targetTradeDate, String reason) {
        return new InternalPredictionCandidateResponse(candidateBatchId, null, null,
                targetTradeDate, List.of(), reason);
    }
}
// AI_GENERATE_END------
