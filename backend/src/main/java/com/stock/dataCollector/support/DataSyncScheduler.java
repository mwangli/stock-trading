package com.stock.dataCollector.support;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import com.stock.dataCollector.service.StockNewsService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据同步定时任务
 * <p>
 * 说明：本类由动态任务调度中心 {@link com.stock.tradingExecutor.job.JobSchedulerService}
 * 通过 JobConfig 中的 beanName + methodName 反射调用，无需使用 @Scheduled 注解。
 * 默认任务配置见 {@link com.stock.tradingExecutor.job.JobBootstrap}。
 * </p>
 *
 * @author mwangli
 * @since 1.0
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
     * 每日收盘后同步所有股票日 K 线数据（多线程并行）
     * <p>
     * 对应任务: dailyStockDataSync
     * Cron: 0 0 18 * * MON-FRI
     * 平台接口不支持单日区间，默认拉取最近约 500 条，内存过滤后仅保存新数据
     * </p>
     */
    public void syncDailyStockData() {
        log.info("========== [数据同步任务] 开始执行每日收盘后日K数据增量同步（{} 线程并行） ==========", PRICE_SYNC_THREADS);
        try {
            List<String> codes = stockDataService.findAllCodes();
            if (codes == null || codes.isEmpty()) {
                log.warn("[数据同步任务] 未找到任何股票代码，跳过日K同步");
                return;
            }

            int total = codes.size();
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger noChangeCount = new AtomicInteger(0);
            AtomicInteger processedCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(total);

            for (String code : codes) {
                priceSyncExecutor.submit(() -> {
                    try {
                        int added = stockDataService.syncLatestPriceData(code);
                        if (added > 0) {
                            successCount.incrementAndGet();
                            int p = processedCount.incrementAndGet();
                            log.info("[数据同步任务] 股票 {} 日K 增量同步新增 {} 条记录，进度: {}/{} ({}%)",
                                    code, added, p, total, String.format("%.2f", p * 100.0 / total));
                        } else {
                            noChangeCount.incrementAndGet();
                            processedCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.error("[数据同步任务] 股票 {} 日K 同步失败: {}", code, e.getMessage(), e);
                        processedCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(4, TimeUnit.HOURS);

            log.info("========== [数据同步任务] 每日日K增量同步完成：共 {} 只股票，新增数据股票 {} 只，无更新股票 {} 只 ==========",
                    total, successCount.get(), noChangeCount.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[数据同步任务] 每日日K同步被中断");
            throw new RuntimeException("每日日K增量同步任务被中断", e);
        } catch (Exception e) {
            log.error("[数据同步任务] 每日日K增量同步任务执行失败", e);
            throw new RuntimeException("每日日K增量同步任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 深度同步所有股票历史数据 (周维护)
     * <p>
     * 对应任务: historicalDataSync
     * Cron: 0 0 2 * * SUN
     *
     * 说明：适合作为“周维护”任务，在冷启动后定期做一次深度校正，
     * 内部可调用 StockDataService.syncHistoricalData / reaggregateAllKLineData 等方法。
     * 当前先保留占位实现，后续可根据实际需要补充更细致的逻辑。
     * </p>
     */
    public void syncAllHistoricalData() {
        log.info("========== [数据同步任务] 深度同步所有股票历史数据 (周维护) 开始 ==========");
        try {
            // 这里暂时只记录日志，避免与冷启动全量同步重复拉取过多数据。
            // 后续可以按需补充：例如对指定问题股票做完整历史重拉 + K线重聚合。
            log.info("[数据同步任务] 当前版本仅记录任务触发，如需具体逻辑请在 DataSyncScheduler.syncAllHistoricalData 中补充实现。");
        } catch (Exception e) {
            log.error("[数据同步任务] 深度同步所有股票历史数据任务执行失败", e);
            throw new RuntimeException("深度历史同步任务失败: " + e.getMessage(), e);
        }
        log.info("========== [数据同步任务] 深度同步所有股票历史数据 (周维护) 结束 ==========");
    }

    /**
     * 全量新闻采集
     * <p>
     * 对应任务: newsFullSync
     * Cron: 0 0 6 * * SUN（每周日 6:00）
     * 每只股票每来源（新闻/公告）采集最新 100 条，用于冷启动或周维护
     * </p>
     */
    public void collectNewsFullSync() {
        log.info("========== [数据同步任务] 开始执行全量新闻采集 ==========");
        try {
            StockNewsService.CollectResult result = stockNewsService.collectAllStockNews(0);
            log.info("[数据同步任务] 全量新闻采集完成: 处理 {} 只股票，新增 {} 条，失败 {} 只",
                    result.processedCount(), result.savedCount(), result.failedCount());
        } catch (Exception e) {
            log.error("[数据同步任务] 全量新闻采集失败", e);
            throw new RuntimeException("全量新闻采集失败: " + e.getMessage(), e);
        }
        log.info("========== [数据同步任务] 全量新闻采集结束 ==========");
    }

    /**
     * 每日增量新闻采集
     * <p>
     * 对应任务: newsDailySync
     * Cron: 0 0 6 * * MON-FRI（每交易日 6:00）
     * 每只股票每来源采集最新 100 条，已存在则跳过，仅入库新数据
     * </p>
     */
    public void collectNewsDailySync() {
        log.info("========== [数据同步任务] 开始执行每日增量新闻采集 ==========");
        try {
            StockNewsService.CollectResult result = stockNewsService.collectAllStockNews(NEWS_DAILY_MAX_STOCKS);
            log.info("[数据同步任务] 每日增量新闻采集完成: 处理 {} 只股票，新增 {} 条，失败 {} 只",
                    result.processedCount(), result.savedCount(), result.failedCount());
        } catch (Exception e) {
            log.error("[数据同步任务] 每日增量新闻采集失败", e);
            throw new RuntimeException("每日增量新闻采集失败: " + e.getMessage(), e);
        }
        log.info("========== [数据同步任务] 每日增量新闻采集结束 ==========");
    }
}

