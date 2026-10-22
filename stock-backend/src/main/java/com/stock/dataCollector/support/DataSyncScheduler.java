// AI_GENERATE_START---- --------
package com.stock.dataCollector.support;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import com.stock.dataCollector.service.StockNewsService;
import com.stock.dataCollector.service.TradingCalendarMaintenanceService;
import com.stock.dataCollector.service.TradingCalendarMaintenanceService.CalendarMaintenanceResult;
import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.tradingExecutor.JobSkippedException;
import com.stock.tradingExecutor.sync.SyncStateService;
import com.stock.tradingExecutor.service.SystemRuntimeConfigService;
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
 * 默认任务配置见 {@link com.stock.tradingExecutor.JobBootstrap}。
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
    /** 每日日K同步并行线程数 */
    private static final int PRICE_SYNC_THREADS = 8;

    private final StockDataService stockDataService;
    private final StockNewsService stockNewsService;
    private final SystemRuntimeConfigService runtimeConfigService;
    private final TradingCalendarService tradingCalendarService;
    private final TradingCalendarMaintenanceService tradingCalendarMaintenanceService;
    private final SyncStateService syncStateService;

    private final ExecutorService priceSyncExecutor = Executors.newFixedThreadPool(PRICE_SYNC_THREADS,
            r -> {
                Thread t = new Thread(r, "price-sync-" + r.hashCode() % 100);
                t.setDaemon(false);
                return t;
            });

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
        runtimeConfigService.requireDataCollectionEnabled();
        log.info("========== [数据同步任务] 开始执行股票列表同步 ==========");
        try {
            StockDataService.SyncResult result = stockDataService.syncStockList();
            log.info("[数据同步任务] 股票列表同步完成: 总数={}, 新增={}, 更新={}, 失败={}, 耗时={}ms",
                    result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(),
                    result.getFailedCount(), result.getCostTimeMs());

            List<StockInfo> sampleStocks = stockDataService.findAllStocks();
            int printCount = Math.min(5, sampleStocks.size());
            for (int i = 0; i < printCount; i++) {
                StockInfo stock = sampleStocks.get(i);
                log.info("[数据同步任务] 样例股票 - 代码: {}, 名称: {}, 市场: {}",
                        stock.getCode(), stock.getName(), stock.getMarket());
            }
        } catch (Exception e) {
            log.error("[数据同步任务] 股票列表同步失败", e);
            throw new RuntimeException("股票列表同步失败: " + e.getMessage(), e);
        }
    }

    /**
     * 同步当前交易日，并回补最近三年的缺失交易日。
     *
     * 日历区间存在缺口时先调用滚动维护服务修复一次，再使用已确认交易日执行行情同步。
     *
     * @throws JobSkippedException 当前日期尚未确认或不是交易日时抛出
     * @throws IllegalStateException 日历修复失败、行情同步失败或历史缺口仍未补齐时抛出
     */
    public void syncDailyStockData() {
        runtimeConfigService.requireDataCollectionEnabled();
        LocalDate today = LocalDate.now();
        LocalDate earliestDate = today.minusYears(3);
        List<LocalDate> tradingDays = loadTradingDaysWithCalendarRepair(earliestDate, today);
        if (!tradingCalendarService.isConfirmedDate(today)) {
            throw new JobSkippedException("交易日历尚未确认: " + today);
        }
        if (!tradingCalendarService.isTradingDay(today)) {
            throw new JobSkippedException("当前日期不是交易日: " + today);
        }

        List<String> codes = stockDataService.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            throw new IllegalStateException("未找到任何股票代码，无法执行日线同步");
        }

        AtomicInteger todaySaved = new AtomicInteger();
        AtomicInteger backfillSaved = new AtomicInteger();
        AtomicInteger unresolved = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(codes.size());
        for (String code : codes) {
            priceSyncExecutor.submit(() -> {
                try {
                    StockDataService.PriceSyncResult result = stockDataService
                            .syncTodayAndBackfill(code, today, earliestDate, tradingDays);
                    todaySaved.addAndGet(result.todaySaved());
                    backfillSaved.addAndGet(result.backfillSaved());
                    unresolved.addAndGet(result.unresolvedDates().size());
                    if (result.unresolvedDates().isEmpty()) {
                        syncStateService.markSuccess("stock-price", code, today);
                        syncStateService.resolveGaps("stock-price", code);
                    } else {
                        syncStateService.recordGap("stock-price", code,
                                result.unresolvedDates().get(0),
                                result.unresolvedDates().get(result.unresolvedDates().size() - 1),
                                "供应商三年窗口未覆盖全部缺失交易日");
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

        try {
            if (!latch.await(4, TimeUnit.HOURS)) {
                throw new IllegalStateException("日线同步超过四小时仍未完成");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("日线同步被中断", exception);
        }
        log.info("[数据同步任务] 日线同步完成: todaySaved={}, backfillSaved={}, unresolved={}, failed={}",
                todaySaved.get(), backfillSaved.get(), unresolved.get(), failed.get());
        if (failed.get() > 0 || unresolved.get() > 0) {
            throw new IllegalStateException("日线同步存在失败或三年窗口内未回补数据");
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
     * 补全历史新闻缺失的事件类型和信息优先级。
     * <p>
     * 可由维护接口手动触发。方法可重复执行，已完整的新闻不会被修改。
     * </p>
     */
    public void backfillNewsMetadata() {
        runtimeConfigService.requireNewsCollectionEnabled();
        log.info("========== [数据同步任务] 开始补全历史新闻元数据 ==========");
        StockNewsService.NewsMetadataBackfillResult result = stockNewsService.backfillMissingMetadata();
        log.info("[数据同步任务] 历史新闻元数据补全完成: 扫描 {} 条，更新 {} 条",
                result.scannedCount(), result.updatedCount());
        log.info("========== [数据同步任务] 历史新闻元数据补全结束 ==========");
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
        runtimeConfigService.requireNewsCollectionEnabled();
        log.info("========== [数据同步任务] 开始执行每日增量新闻采集 ==========");
        try {
            StockNewsService.CollectResult result = stockNewsService.collectAllStockNews(NEWS_DAILY_MAX_STOCKS);
            log.info("[数据同步任务] 每日增量新闻采集完成: batchId={}，处理 {} 只，新增 {} 条，有新增 {} 只，无新增 {} 只，部分失败 {} 只，失败 {} 只",
                    result.batchId(), result.processedCount(), result.savedCount(), result.withChangeCount(),
                    result.noChangeCount(), result.partialCount(), result.failedCount());
            if (result.partialCount() > 0 || result.failedCount() > 0) {
                throw new IllegalStateException("每日新闻同步存在部分失败或失败股票");
            }
        } catch (Exception e) {
            log.error("[数据同步任务] 每日增量新闻采集失败", e);
            throw new RuntimeException("每日增量新闻采集失败: " + e.getMessage(), e);
        }
        log.info("========== [数据同步任务] 每日增量新闻采集结束 ==========");
    }
}
// AI_GENERATE_END -----------
