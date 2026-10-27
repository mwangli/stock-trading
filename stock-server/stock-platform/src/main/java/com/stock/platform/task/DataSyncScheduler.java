// AI_GENERATE_START---------------------------------------------
package com.stock.platform.task;

import com.stock.data.dto.ExternalNewsCollectionResult;
import com.stock.data.dto.NewsCollectionBackfillResult;
import com.stock.data.dto.StockListSyncResult;
import com.stock.data.dto.DailyPriceSyncResult;
import com.stock.data.dto.HistoricalPriceSyncResult;

import com.stock.data.service.ExternalNewsCollectionService;
import com.stock.data.service.NewsCollectionBackfillService;
import com.stock.data.service.StockListSyncService;
import com.stock.data.service.StockPriceService;
import com.stock.data.service.StockQueryService;
import com.stock.data.service.StockNewsService;
import com.stock.data.service.TradingCalendarMaintenanceService;
import com.stock.data.service.TradingCalendarMaintenanceService.CalendarMaintenanceResult;
import com.stock.data.service.TradingCalendarService;
import com.stock.model.service.PredictionEvaluationQueueService;
import com.stock.model.service.NewsFeatureSnapshotBackfillService;
import com.stock.model.dto.NewsFeatureSnapshotBackfillResult;
import com.stock.platform.exception.JobSkippedException;
import com.stock.trading.service.SyncStateService;
import com.stock.trading.service.SyncStateService.SyncStateSnapshot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据同步定时任务
 * <p>
 * 说明：本类提供数据同步业务方法，由固定 Quartz Job 类直接调用。
 * 默认任务配置由 stock-job 模块统一维护。
 * </p>
 *
 * @author mwangli
 * @since 2026-10-20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncScheduler {

    /** 增量新闻采集每次最多处理的股票数，0 表示不限制 */
    private static final int NEWS_DAILY_MAX_STOCKS = 0;
    /** 每日日K同步最小并行线程数，保留 I/O 等待期间的并发能力。 */
    private static final int MIN_PRICE_SYNC_THREADS = 2;
    /** 每日日K同步最大并行线程数，避免对行情供应商和 MongoDB 造成突发压力。 */
    private static final int MAX_PRICE_SYNC_THREADS = 16;
    /** JVM 可使用的处理器数量，Java 17 会感知容器 CPU 限额。 */
    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    /** 每日日K同步并行线程数，HTTP 和 MongoDB I/O 场景按 CPU 核数的两倍配置。 */
    private static final int PRICE_SYNC_THREADS = Math.max(
            MIN_PRICE_SYNC_THREADS,
            Math.min(MAX_PRICE_SYNC_THREADS, AVAILABLE_PROCESSORS * 2));
    /** 行情同步线程编号。 */
    private static final AtomicInteger PRICE_SYNC_THREAD_SEQUENCE = new AtomicInteger(1);
    /** 行情供应商历史回补窗口年数 */
    private static final int PRICE_BACKFILL_YEARS = 3;

    private final StockListSyncService stockListSyncService;
    private final StockPriceService stockPriceService;
    private final StockQueryService stockQueryService;
    private final StockNewsService stockNewsService;
    private final ExternalNewsCollectionService externalNewsCollectionService;
    private final NewsCollectionBackfillService newsCollectionBackfillService;
    private final NewsFeatureSnapshotBackfillService newsFeatureSnapshotBackfillService;
    private final TradingCalendarService tradingCalendarService;
    private final TradingCalendarMaintenanceService tradingCalendarMaintenanceService;
    private final SyncStateService syncStateService;
    private final PredictionEvaluationQueueService predictionEvaluationQueueService;

    private final ExecutorService priceSyncExecutor = Executors.newFixedThreadPool(PRICE_SYNC_THREADS,
            r -> {
                Thread t = new Thread(r, "price-sync-" + PRICE_SYNC_THREAD_SEQUENCE.getAndIncrement());
                t.setDaemon(false);
                return t;
            });

    /** 停止数据同步编排线程池并终止后续异步任务。 */
    @PreDestroy
    public void shutdown() {
        priceSyncExecutor.shutdown();
        try {
            if (!priceSyncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                priceSyncExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            priceSyncExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 每周同步证券 API 股票代码和名称
     * <p>
     * 对应任务: stockListSync
     * Cron: 0 0 1 * * SUN
     */
    public void syncStockListDaily() {
        log.info("========== [数据同步任务] 开始执行股票列表同步 ==========");
        try {
            StockListSyncResult result = stockListSyncService.syncStockList();
            log.info("[数据同步任务] 股票列表同步完成: 总数={}, 新增={}, 更新={}, 失败={}, 耗时={}ms",
                    result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(),
                    result.getFailedCount(), result.getCostTimeMs());

        } catch (Exception e) {
            log.error("[数据同步任务] 股票列表同步失败", e);
            throw new RuntimeException("股票列表同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步当前已确认交易日的全市场日线增量。
     *
     * 每日任务只请求当天数据，不扫描或回补历史缺口。停牌、新股和供应商当日缺失仅记录为
     * 待回补状态，不阻断其他股票；基础设施异常仍在全部股票处理完成后报告任务失败。
     *
     * @throws JobSkippedException 当前日期尚未确认或不是交易日时抛出
     * @throws IllegalStateException 任务超时、中断或出现基础设施异常时抛出
     */
    public void syncDailyStockData() {
        LocalDate today = LocalDate.now();
        if (!tradingCalendarService.isConfirmedDate(today)) {
            throw new JobSkippedException("交易日历尚未确认: " + today);
        }
        if (!tradingCalendarService.isTradingDay(today)) {
            throw new JobSkippedException("当前日期不是交易日: " + today);
        }

        List<String> codes = stockQueryService.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            throw new IllegalStateException("未找到任何股票代码，无法执行日线同步");
        }
        log.info("[数据同步任务] 日线同步开始: availableProcessors={}, threads={}, stockCount={}",
                AVAILABLE_PROCESSORS, PRICE_SYNC_THREADS, codes.size());

        AtomicInteger saved = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger apiRequests = new AtomicInteger();
        AtomicInteger unavailable = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(codes.size());
        for (String code : codes) {
            priceSyncExecutor.submit(() -> {
                try {
                    DailyPriceSyncResult result = stockPriceService
                            .syncDailyPrice(code, today);
                    saved.addAndGet(result.savedCount());
                    apiRequests.addAndGet(result.apiRequestCount());
                    switch (result.status()) {
                        case SAVED -> syncStateService.markSuccess("stock-price-daily", code, today);
                        case ALREADY_PRESENT -> {
                            skipped.incrementAndGet();
                            syncStateService.markSuccess("stock-price-daily", code, today);
                        }
                        case NOT_LISTED -> {
                            skipped.incrementAndGet();
                            log.info("[数据同步任务] 股票 {} 在 {} 尚未上市，跳过当日日线同步", code, today);
                        }
                        case NO_DATA -> {
                            unavailable.incrementAndGet();
                            syncStateService.recordGap(
                                    "stock-price", code, today, today, "SUPPLIER_NO_DATA");
                            log.warn("[数据同步任务] 股票 {} 在 {} 无可用日线，已登记历史回补",
                                    code, today);
                        }
                    }
                } catch (Exception exception) {
                    failed.incrementAndGet();
                    syncStateService.recordGap("stock-price", code, today, today, exception.getMessage());
                    log.error("[数据同步任务] 股票 {} 日线同步失败: {}", code, exception.getMessage(), exception);
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitPriceSync(latch, "每日日线增量同步");
        log.info("[数据同步任务] 每日日线增量同步完成: saved={}, skipped={}, apiRequests={}, "
                        + "unavailable={}, failed={}",
                saved.get(), skipped.get(), apiRequests.get(), unavailable.get(), failed.get());
        if (failed.get() > 0) {
            throw new IllegalStateException("每日日线增量同步存在基础设施异常，失败股票数: " + failed.get());
        }
        int evaluationQueuedCount = predictionEvaluationQueueService.resumeAvailable();
        log.info("[数据同步任务] 已唤醒模型评估队列: evaluationQueued={}", evaluationQueuedCount);
    }

    /**
     * 回补最近三年已确认交易日的历史日线缺口。
     *
     * 该任务与每日增量完全分离，按低频计划执行。单只股票的停牌、历史缺失或供应商无数据
     * 只保留为 OPEN 缺口，不导致全市场回补失败。
     *
     * @throws IllegalStateException 交易日历不可用、任务超时、中断或出现基础设施异常时抛出
     */
    public void backfillHistoricalStockData() {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate earliestDate = endDate.minusYears(PRICE_BACKFILL_YEARS);
        List<LocalDate> confirmedTradingDays = loadTradingDaysWithCalendarRepair(earliestDate, endDate);
        List<String> codes = stockQueryService.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            throw new IllegalStateException("未找到任何股票代码，无法执行历史日线回补");
        }
        log.info("[数据同步任务] 历史日线回补开始: range={}~{}, confirmedTradingDays={}, "
                        + "availableProcessors={}, threads={}, stockCount={}",
                earliestDate, endDate, confirmedTradingDays.size(),
                AVAILABLE_PROCESSORS, PRICE_SYNC_THREADS, codes.size());

        AtomicInteger saved = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        AtomicInteger apiRequests = new AtomicInteger();
        AtomicInteger unresolved = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(codes.size());
        for (String code : codes) {
            priceSyncExecutor.submit(() -> {
                try {
                    SyncStateSnapshot syncState = syncStateService.findState("stock-price", code)
                            .orElse(null);
                    HistoricalPriceSyncResult result = stockPriceService
                            .backfillHistoricalPrices(
                                    code,
                                    earliestDate,
                                    endDate,
                                    confirmedTradingDays,
                                    syncState != null && syncState.openGap()
                                            ? syncState.gapStartDate() : null,
                                    syncState != null && syncState.openGap()
                                            ? syncState.gapEndDate() : null);
                    saved.addAndGet(result.savedCount());
                    apiRequests.addAndGet(result.apiRequestCount());
                    if (result.skipped()) {
                        skipped.incrementAndGet();
                    }
                    unresolved.addAndGet(result.unresolvedDates().size());
                    if (result.unresolvedDates().isEmpty()) {
                        syncStateService.markSuccess("stock-price", code, endDate);
                    } else {
                        syncStateService.recordGap(
                                "stock-price", code,
                                result.unresolvedDates().get(0),
                                result.unresolvedDates().get(result.unresolvedDates().size() - 1),
                                "历史回补后仍存在未解决的已确认交易日");
                    }
                } catch (Exception exception) {
                    failed.incrementAndGet();
                    syncStateService.recordGap(
                            "stock-price", code, earliestDate, endDate, exception.getMessage());
                    log.error("[数据同步任务] 股票 {} 历史日线回补异常: {}",
                            code, exception.getMessage(), exception);
                } finally {
                    latch.countDown();
                }
            });
        }

        awaitPriceSync(latch, "历史日线回补");
        log.info("[数据同步任务] 历史日线回补完成: saved={}, skipped={}, apiRequests={}, "
                        + "unresolved={}, failed={}",
                saved.get(), skipped.get(), apiRequests.get(), unresolved.get(), failed.get());
        if (failed.get() > 0) {
            throw new IllegalStateException("历史日线回补存在基础设施异常，失败股票数: " + failed.get());
        }
    }

    private void awaitPriceSync(CountDownLatch latch, String taskName) {
        try {
            if (!latch.await(4, TimeUnit.HOURS)) {
                throw new IllegalStateException(taskName + "超过四小时仍未完成");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(taskName + "被中断", exception);
        }
    }

    private List<LocalDate> loadTradingDaysWithCalendarRepair(LocalDate startDate, LocalDate endDate) {
        try {
            return tradingCalendarService.tradingDaysBetween(startDate, endDate);
        } catch (IllegalStateException exception) {
            log.warn("[数据同步任务] 交易日历区间不完整，执行一次滚动维护后重试: range={}~{}, reason={}",
                    startDate, endDate, exception.getMessage());
            CalendarMaintenanceResult result = tradingCalendarMaintenanceService.maintainRollingCalendar();
            log.info("[数据同步任务] 交易日历修复完成: range={}~{}, inserted={}, confirmed={}, unverified={}",
                    result.startDate(), result.endDate(), result.insertedCount(),
                    result.confirmedCount(), result.unverifiedCount());
            return tradingCalendarService.tradingDaysBetween(startDate, endDate);
        }
    }

    /**
     * 按当前采集治理版本回填历史新闻和事件事实。
     *
     * 任务按持久化游标分批执行，单次运行达到资源上限后保留待续跑状态。
     */
    public void backfillNewsCollectionQuality() {
        log.info("========== [数据同步任务] 开始回填新闻采集治理 ==========");
        NewsCollectionBackfillResult result = newsCollectionBackfillService.backfill();
        log.info("[数据同步任务] 新闻采集治理回填完成: version={}, scanned={}, updated={}, facts={}, "
                        + "duplicates={}, ignored={}, typeCounts={}, roleCounts={}, failed={}, "
                        + "lastProcessedId={}, completed={}",
                result.pipelineVersion(), result.scannedCount(), result.updatedCount(),
                result.eventFactCount(), result.duplicateCount(), result.ignoredCount(),
                result.documentTypeCounts(), result.documentRoleCounts(), result.failedCount(),
                result.lastProcessedId(), result.completed());
        log.info("========== [数据同步任务] 新闻采集治理回填结束 ==========");
    }

    /**
     * 分批回填 `temporal-news-v1` 股票日级快照并执行点时审计。
     *
     * @throws IllegalStateException 快照生成或审计失败时抛出
     */
    public void backfillNewsFeatureSnapshots() {
        log.info("========== [数据同步任务] 开始回填新闻日级特征快照 ==========");
        NewsFeatureSnapshotBackfillResult result = newsFeatureSnapshotBackfillService.backfill();
        log.info("[数据同步任务] 新闻快照回填完成: scanned={}, generated={}, skipped={}, failed={}, lastBarId={}, completed={}, audit={}",
                result.scannedCount(), result.generatedCount(), result.skippedCount(), result.failedCount(),
                result.lastProcessedBarId(), result.completed(), result.audit());
        log.info("========== [数据同步任务] 新闻日级特征快照回填结束 ==========");
    }
    /**
     * 每日增量新闻采集
     * <p>
     * 对应任务: newsDailySync
     * Cron: 0 0 5 * * MON-FRI（每交易日 5:00）
     * 每只股票每来源检查最新一页，已存在则跳过详情请求，仅入库新数据并记录同步水位
     * </p>
     */
    public void collectNewsDailySync() {
        log.info("========== [数据同步任务] 开始执行每日增量新闻采集 ==========");
        try {
            StockNewsService.CollectResult result = stockNewsService.collectAllStockNews(NEWS_DAILY_MAX_STOCKS);
            log.info("[数据同步任务] 每日增量新闻采集完成: batchId={}，处理 {} 只，新增 {} 条，有新增 {} 只，无新增 {} 只，部分失败 {} 只，失败 {} 只",
                    result.batchId(), result.processedCount(), result.savedCount(), result.withChangeCount(),
                    result.noChangeCount(), result.partialCount(), result.failedCount());
            if (result.partialCount() > 0 || result.failedCount() > 0) {
                throw new IllegalStateException("每日新闻同步存在部分失败或失败股票");
            }
            ExternalNewsCollectionResult externalResult = externalNewsCollectionService.collectIncremental();
            if (externalResult.enabled()) {
                log.info("[数据同步任务] 外部新闻来源采集完成: configuredSources={}, successfulSources={}, "
                                + "fetched={}, inserted={}, eventDocuments={}, errors={}",
                        externalResult.configuredSourceCount(), externalResult.successfulSourceCount(),
                        externalResult.fetchedCount(), externalResult.insertedCount(),
                        externalResult.eventFactCount(), externalResult.errors());
                if (externalResult.configuredSourceCount() > 0
                        && externalResult.successfulSourceCount() == 0) {
                    throw new IllegalStateException("已启用的外部新闻来源全部采集失败");
                }
            }
        } catch (Exception e) {
            log.error("[数据同步任务] 每日增量新闻采集失败", e);
            throw new RuntimeException("每日增量新闻采集失败: " + e.getMessage(), e);
        }
        log.info("========== [数据同步任务] 每日增量新闻采集结束 ==========");
    }
}
// AI_GENERATE_END---------------------------------------------
