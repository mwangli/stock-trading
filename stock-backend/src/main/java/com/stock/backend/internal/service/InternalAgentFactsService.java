// AI_GENERATE_START -
package com.stock.backend.internal.service;

import com.stock.backend.internal.dto.InternalBrokerFactsResponse;
import com.stock.backend.internal.dto.InternalBrokerStatusResponse;
import com.stock.backend.internal.dto.InternalJobDiagnosticResponse;
import com.stock.backend.internal.dto.InternalLatestPriceResponse;
import com.stock.backend.internal.dto.InternalModelOverviewResponse;
import com.stock.backend.internal.dto.InternalPerformanceOverviewResponse;
import com.stock.backend.internal.dto.InternalPredictionCandidateItem;
import com.stock.backend.internal.dto.InternalPredictionCandidateResponse;
import com.stock.backend.internal.dto.InternalStrategyStateResponse;
import com.stock.backend.internal.dto.InternalTradeExecutionGateResponse;
import com.stock.backend.internal.dto.InternalTradingCalendarResponse;
import com.stock.base.job.JobExecutionQueryPort;
import com.stock.base.job.JobExecutionSnapshot;
import com.stock.data.entity.StockPrice;
import com.stock.data.service.StockPriceService;
import com.stock.data.service.TradingCalendarService;
import com.stock.model.dto.ModelOverviewResponse;
import com.stock.model.dto.ModelTrainingRunResponse;
import com.stock.model.dto.ModelVersionResponse;
import com.stock.model.service.ModelOperationsQueryService;
import com.stock.strategy.config.StrategyStateManager;
import com.stock.strategy.dto.StrategyStateDto;
import com.stock.strategy.engine.StockSelector;
import com.stock.strategy.entity.StockRanking;
import com.stock.strategy.repository.RankingRepository;
import com.stock.trading.dto.BrokerReadStatusDto;
import com.stock.trading.dto.PerformanceOverviewDto;
import com.stock.trading.entity.Position;
import com.stock.trading.execution.BrokerAdapter;
import com.stock.trading.execution.TradingProperties;
import com.stock.trading.service.BrokerReadService;
import com.stock.trading.service.PerformanceReportService;
import com.stock.trading.vo.AccountStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

    private final StockPriceService stockPriceService;
    private final TradingCalendarService tradingCalendarService;
    private final StrategyStateManager strategyStateManager;
    private final RankingRepository rankingRepository;
    private final BrokerReadService brokerReadService;
    private final BrokerAdapter brokerAdapter;
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
        StockPrice price = stockPriceService.getLatestPrice(stockCode);
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
        return new InternalTradingCalendarResponse(tradeDate, tradingDay,
                tradingCalendarService.isConfirmedDate(tradeDate),
                tradingDay ? tradeDate : tradingCalendarService.nextTradingDay(tradeDate));
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
        List<StockRanking> rankings = rankingRepository
                .findByCandidateBatchIdOrderByRankAsc(candidateBatchId);
        if (rankings.isEmpty()) {
            return emptyCandidates(candidateBatchId, null,
                    "指定候选批次没有排名记录，无法确认候选排序；请检查候选生成任务、目标交易日和批次是否已过期");
        }
        StockRanking first = rankings.get(0);
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
        return rankingRepository.findFirstByTargetTradeDateOrderByCalculateTimeDesc(targetTradeDate)
                .map(item -> predictionCandidates(item.getCandidateBatchId()))
                .orElseGet(() -> emptyCandidates(null, targetTradeDate,
                        "目标交易日没有候选排名快照，不能判断候选排序或生成买入计划"));
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
        AccountStatus account = brokerAdapter.getAccountInfo();
        List<Position> positions = brokerAdapter.getPositions();
        int orderCount = brokerAdapter.getTodayOrderSnapshots().size();
        int fillCount = brokerAdapter.getTodayFillSnapshots().size();
        return new InternalBrokerFactsResponse(account == null ? null : account.getAvailableCash(),
                account == null ? null : account.getTotalAssets(), positions.size(), orderCount,
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
        boolean brokerAuthenticated = brokerAdapter.isAuthenticated();
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
// AI_GENERATE_END -
