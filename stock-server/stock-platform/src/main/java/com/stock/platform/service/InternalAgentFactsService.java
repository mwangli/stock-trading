// AI_GENERATE_START------------
package com.stock.platform.service;

import com.stock.platform.dto.InternalBrokerHistoryResponse;
import com.stock.platform.dto.InternalBrokerFactsResponse;
import com.stock.platform.dto.InternalBrokerStatusResponse;
import com.stock.platform.dto.InternalJobOperationsResponse;
import com.stock.platform.dto.InternalJobDiagnosticResponse;
import com.stock.platform.dto.InternalLatestPriceResponse;
import com.stock.platform.dto.InternalMarketDataQualityResponse;
import com.stock.platform.dto.InternalModelOverviewResponse;
import com.stock.platform.dto.InternalModelOperationsResponse;
import com.stock.platform.dto.InternalPerformanceOverviewResponse;
import com.stock.platform.dto.InternalMarketAdvancerItemResponse;
import com.stock.platform.dto.InternalMarketAdvancersResponse;
import com.stock.platform.dto.InternalPerformanceAnalysisItemResponse;
import com.stock.platform.dto.InternalPerformanceAnalysisResponse;
import com.stock.platform.dto.InternalPredictionCandidateItem;
import com.stock.platform.dto.InternalPredictionCandidateResponse;
import com.stock.platform.dto.InternalStockDirectoryResponse;
import com.stock.platform.dto.InternalTradingCalendarResponse;
import com.stock.platform.dto.JobExecutionSnapshot;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.service.MarketDataQueryService;
import com.stock.data.service.StockQueryService;
import com.stock.data.service.TradingCalendarService;
import com.stock.model.dto.ModelOverviewResponse;
import com.stock.model.dto.ModelTrainingRunResponse;
import com.stock.model.dto.ModelVersionResponse;
import com.stock.model.dto.PredictionCandidateBatchDto;
import com.stock.model.dto.PredictionCandidateItemDto;
import com.stock.model.service.ModelOperationsQueryService;
import com.stock.model.service.ModelPredictionQueryService;
import com.stock.model.service.PredictionCandidateQueryService;
import com.stock.trading.dto.BrokerReadStatusDto;
import com.stock.trading.dto.BrokerHistoryQueryRequest;
import com.stock.trading.dto.PerformanceOverviewDto;
import com.stock.trading.dto.BrokerAccountDto;
import com.stock.trading.service.BrokerReadService;
import com.stock.trading.service.PerformanceReportService;
import com.stock.platform.dto.JobExecutionResponse;
import com.stock.platform.service.JobManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
    private final BrokerReadService brokerReadService;
    private final PerformanceReportService performanceReportService;
    private final ModelOperationsQueryService modelOperationsQueryService;
    private final ModelPredictionQueryService modelPredictionQueryService;
    private final JobExecutionService jobExecutionService;
    private final JobManagementService jobManagementService;
    private final PredictionCandidateQueryService predictionCandidateQueryService;

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
     * 查询指定候选批次摘要。
     *
     * @param predictionBatchId 预测批次标识
     * @return 模型排名摘要
     */
    public InternalPredictionCandidateResponse predictionCandidates(String predictionBatchId) {
        PredictionCandidateBatchDto batch = predictionCandidateQueryService.getLatestUsableBatch(LocalDate.now());
        if (predictionBatchId != null && !predictionBatchId.isBlank()
                && !predictionBatchId.equals(batch.getBatchId())) {
            return emptyCandidates(predictionBatchId, batch.getTargetTradeDate(), "指定预测批次不是当日最新可用批次");
        }
        return toPredictionCandidates(batch);
    }

    private InternalPredictionCandidateResponse toPredictionCandidates(PredictionCandidateBatchDto batch) {
        List<PredictionCandidateItemDto> ranked = batch.getItems().stream()
                .filter(item -> item.getEffectiveModelScore() != null)
                .sorted(java.util.Comparator.comparing(PredictionCandidateItemDto::getEffectiveModelScore).reversed())
                .limit(20).toList();
        List<InternalPredictionCandidateItem> items = new java.util.ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            PredictionCandidateItemDto item = ranked.get(index);
            items.add(new InternalPredictionCandidateItem(item.getStockCode(), item.getStockName(),
                    index + 1, item.getEffectiveModelScore(), item.getEffectiveEstimatedNetReturn()));
        }
        return new InternalPredictionCandidateResponse(batch.getBatchId(), batch.getModelVersion(),
                batch.getTargetTradeDate(), items,
                "已读取模型排名，共 " + batch.getItems().size() + " 条，展示前 " + items.size() + " 条");
    }

    /**
     * 查询目标交易日的最新候选批次摘要。
     *
     * @param targetTradeDate 目标交易日
     * @return 最新候选批次摘要
     */
    public InternalPredictionCandidateResponse latestPredictionCandidates(LocalDate targetTradeDate) {
        PredictionCandidateBatchDto batch = predictionCandidateQueryService.getLatestUsableBatch(targetTradeDate);
        return toPredictionCandidates(batch);
    }

    /**
     * 查询券商只读状态。
     *
     * @return 券商状态
     */
    public InternalBrokerStatusResponse brokerStatus() {
        BrokerReadStatusDto status = brokerReadService.getStatus();
        return new InternalBrokerStatusResponse(status.getBrokerName(), status.getTradingMode(),
                status.isAuthenticated(), LocalDateTime.now());
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
            com.stock.trading.dto.PerformanceAnalysisDto result) {
        return new InternalPerformanceAnalysisResponse(
                result.getYearMonth(), result.getAsOf(), result.getGrossProfit(), result.getNetProfit(),
                result.getTotalFees(), result.getReturnRate(), result.getTradeCount(),
                result.getWinningTradeCount(), result.getLosingTradeCount(), result.getWinRate(),
                result.getTopGains().stream().map(this::toPerformanceAnalysisItem).toList(),
                result.getTopLosses().stream().map(this::toPerformanceAnalysisItem).toList(),
                result.getAvailabilityReason());
    }

    private InternalPerformanceAnalysisItemResponse toPerformanceAnalysisItem(
            com.stock.trading.dto.PerformanceAnalysisItemDto item) {
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
        JobExecutionSnapshot snapshot = jobExecutionService.findLatest(jobName);
        return snapshot == null
                ? new InternalJobDiagnosticResponse(jobName, "UNKNOWN", "没有运行记录")
                : new InternalJobDiagnosticResponse(jobName, snapshot.status(), snapshot.errorMessage());
    }

    /**
     * 查询股票目录和当前行情摘要。
     *
     * @param keyword 股票代码或名称关键字，可为空
     * @param limit 最大返回数量，范围 1 到 20
     * @return 股票目录事实
     */
    public InternalStockDirectoryResponse stockDirectory(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        var result = stockQueryService.queryStockInfoPage(1, safeLimit, keyword, "updateTime", "desc");
        List<String> items = result.getItems().stream()
                .map(item -> String.format(Locale.ROOT,
                        "%s %s | 市场=%s | 价格=%s | 涨跌幅=%s | 换手率=%s | 更新时间=%s",
                        item.getCode(), item.getName(), item.getMarket(), item.getPrice(),
                        item.getChangePercent(), item.getTurnoverRate(), item.getUpdateTime()))
                .toList();
        return new InternalStockDirectoryResponse(keyword, result.getTotal(), items, LocalDateTime.now(),
                items.isEmpty() ? "没有匹配的可交易股票" : "查询成功");
    }

    /**
     * 查询股票基础数据规模、分布和完整性。
     *
     * @return 市场数据质量事实
     */
    public InternalMarketDataQualityResponse marketDataQuality() {
        var statistics = stockQueryService.getStatistics();
        var validation = stockQueryService.validateData();
        List<String> distribution = statistics.getMarketDistribution().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        List<String> completeness = statistics.getCompletenessRate().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        List<String> missing = validation.getValidation().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
        return new InternalMarketDataQualityResponse(statistics.getTotalCount(), distribution,
                completeness, missing, validation.isValid(), validation.getDataQuality(), LocalDateTime.now());
    }

    /**
     * 查询模型版本、训练运行、预测批次或预测能力摘要。
     *
     * @param mode 查询模式
     * @param referenceId 预测批次标识，查询最新能力时可为空
     * @param limit 最大返回数量，范围 1 到 20
     * @return 模型运维事实
     */
    public InternalModelOperationsResponse modelOperations(String mode, String referenceId, int limit) {
        String normalized = normalizeMode(mode, "VERSIONS");
        int safeLimit = Math.max(1, Math.min(limit, 20));
        LocalDateTime asOf = LocalDateTime.now();
        return switch (normalized) {
            case "TRAINING_RUNS" -> {
                var result = modelOperationsQueryService.listTrainingRuns(1, safeLimit);
                List<String> items = result.getItems().stream()
                        .map(item -> item.getId() + " | 状态=" + item.getStatus()
                                + " | 阶段=" + item.getProgressStage()
                                + " | 进度=" + item.getProgressPercent()
                                + "% | 候选版本=" + item.getCandidateModelVersionId()
                                + " | 创建时间=" + item.getCreatedAt()
                                + " | 错误=" + item.getErrorMessage())
                        .toList();
                yield new InternalModelOperationsResponse(normalized, result.getTotal(), items,
                        "模型训练运行记录", asOf);
            }
            case "PREDICTION_BATCHES" -> {
                var result = modelPredictionQueryService.listBatches(1, safeLimit);
                List<String> items = result.getItems().stream()
                        .map(item -> item.getId() + " | 模型=" + item.getModelVersion()
                                + " | 基准日=" + item.getBaseTradeDate()
                                + " | 目标日=" + item.getTargetTradeDate()
                                + " | 状态=" + item.getStatus()
                                + " | 成功=" + item.getSuccessCount() + "/" + item.getTotalStocks()
                                + " | 能力=" + item.getAbilityLevel()
                                + " | 交易门禁=" + item.getTradingEligibility())
                        .toList();
                yield new InternalModelOperationsResponse(normalized, result.getTotal(), items,
                        "模型预测批次记录", asOf);
            }
            case "PREDICTION_ABILITY" -> {
                var ability = modelPredictionQueryService.getAbility(referenceId);
                List<String> items = List.of("批次=" + ability.getBatchId()
                        + " | 模型=" + ability.getModelVersion()
                        + " | 预测数=" + ability.getPredictedCount()
                        + " | 已评估=" + ability.getEvaluatedCount()
                        + " | 方向准确率=" + ability.getDirectionAccuracy()
                        + " | RankIC=" + ability.getSpearmanRankIc()
                        + " | Top10%收益=" + ability.getTopDecileAverageReturn()
                        + " | 能力等级=" + ability.getAbilityLevel()
                        + " | 交易门禁=" + ability.getTradingEligibility()
                        + " | 说明=" + ability.getAbilityMessage());
                yield new InternalModelOperationsResponse(normalized, 1, items,
                        "模型预测能力概要", asOf);
            }
            case "VERSIONS" -> {
                var result = modelOperationsQueryService.listVersions(1, safeLimit);
                List<String> items = result.getItems().stream()
                        .map(item -> item.getId() + " | 版本=" + item.getModelVersion()
                                + " | 状态=" + item.getStatus()
                                + " | 架构=" + item.getArchitectureType()
                                + " | RankIC=" + item.getWalkForwardMeanRankIc()
                                + " | Top10%净收益=" + item.getWalkForwardTopDecileNetReturn()
                                + " | 激活=" + item.isActive()
                                + " | 创建时间=" + item.getCreatedAt())
                        .toList();
                yield new InternalModelOperationsResponse(normalized, result.getTotal(), items,
                        "模型版本记录", asOf);
            }
            default -> throw new IllegalArgumentException("不支持的模型运维查询模式: " + normalized);
        };
    }

    /**
     * 查询固定任务清单或最近执行记录。
     *
     * @param mode 查询模式
     * @param jobName 任务名称，可为空
     * @param limit 最大返回数量，范围 1 到 20
     * @return 调度任务事实
     */
    public InternalJobOperationsResponse jobOperations(String mode, String jobName, int limit) {
        String normalized = normalizeMode(mode, "JOBS");
        int safeLimit = Math.max(1, Math.min(limit, 20));
        LocalDateTime asOf = LocalDateTime.now();
        if ("RUNS".equals(normalized)) {
            var result = jobManagementService.listRuns(jobName, null, null, null, null, 1, safeLimit);
            List<String> items = result.getItems().stream().map(this::summarizeJobRun).toList();
            return new InternalJobOperationsResponse(normalized, result.getTotal(), items,
                    "任务执行记录", asOf);
        }
        if (!"JOBS".equals(normalized)) {
            throw new IllegalArgumentException("不支持的任务查询模式: " + normalized);
        }
        List<String> items = jobManagementService.listJobs().getItems().stream()
                .filter(item -> jobName == null || jobName.isBlank() || item.getJobName().equals(jobName))
                .limit(safeLimit)
                .map(item -> item.getJobName() + " | 模块=" + item.getModule()
                        + " | Cron=" + item.getCronExpression()
                        + " | 启用=" + item.isEnabled()
                        + " | 状态=" + item.getTaskStatus()
                        + " | 最近运行=" + summarizeJobRun(item.getLatestRun()))
                .toList();
        return new InternalJobOperationsResponse(normalized, items.size(), items,
                "固定调度任务状态", asOf);
    }

    /**
     * 查询最多 31 个自然日的脱敏历史委托或成交。
     *
     * @param type 查询类型，ORDERS 或 FILLS
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @param limit 最大返回数量，范围 1 到 20
     * @return 券商历史事实
     */
    public InternalBrokerHistoryResponse brokerHistory(
            String type, LocalDate startDate, LocalDate endDate, int limit) {
        String normalized = normalizeMode(type, "ORDERS");
        int safeLimit = Math.max(1, Math.min(limit, 20));
        BrokerHistoryQueryRequest request = new BrokerHistoryQueryRequest();
        request.setStartDate(startDate);
        request.setEndDate(endDate);
        if ("FILLS".equals(normalized)) {
            var result = brokerReadService.getHistoryFills(request);
            List<String> items = result.getItems().stream().limit(safeLimit)
                    .map(item -> item.getFillTime() + " | " + item.getStockCode() + " " + item.getStockName()
                            + " | " + item.getDirection() + " | 价格=" + item.getFillPrice()
                            + " | 数量=" + item.getFillQuantity() + " | 金额=" + item.getFillAmount())
                    .toList();
            return new InternalBrokerHistoryResponse(normalized, startDate, endDate,
                    result.getTotal(), items, LocalDateTime.now());
        }
        if (!"ORDERS".equals(normalized)) {
            throw new IllegalArgumentException("不支持的券商历史查询类型: " + normalized);
        }
        var result = brokerReadService.getHistoryOrders(request);
        List<String> items = result.getItems().stream().limit(safeLimit)
                .map(item -> item.getOrderTime() + " | " + item.getStockCode() + " " + item.getStockName()
                        + " | " + item.getDirection() + " | 状态=" + item.getStatus()
                        + " | 委托=" + item.getOrderPrice() + "x" + item.getOrderQuantity()
                        + " | 成交=" + item.getAverageFillPrice() + "x" + item.getFilledQuantity()
                        + " | 费用估算=" + item.getCommission())
                .toList();
        return new InternalBrokerHistoryResponse(normalized, startDate, endDate,
                result.getTotal(), items, LocalDateTime.now());
    }

    private String summarizeJobRun(JobExecutionResponse run) {
        if (run == null) {
            return "无运行记录";
        }
        return run.getRunId() + " | 状态=" + run.getStatus()
                + " | 开始=" + run.getStartedTime()
                + " | 耗时毫秒=" + run.getDurationMs()
                + " | 错误=" + run.getErrorMessage()
                + " | 结果=" + run.getResultSummary();
    }

    private String normalizeMode(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
    }

    private InternalPredictionCandidateResponse emptyCandidates(
            String candidateBatchId, LocalDate targetTradeDate, String reason) {
        return new InternalPredictionCandidateResponse(candidateBatchId, null,
                targetTradeDate, List.of(), reason);
    }
}
// AI_GENERATE_END------------
