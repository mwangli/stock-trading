package com.stock.dataCollector.scheduled;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import com.stock.dataCollector.service.StockNewsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 数据同步定时任务
 * 统一处理股票数据和新闻的定时同步
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncScheduler {

    private final StockDataService stockDataService;
    private final StockNewsService stockNewsService;

    // ==================== 股票列表同步 ====================

    /**
     * 每天凌晨1点同步股票列表到MySQL
     */
    @Scheduled(cron = "0 0 1 * * SUN")
    public void syncStockListDaily() {
        log.info("========== [定时任务] 开始同步股票列表(每周一次) ==========");
        
        try {
            StockDataService.SyncResult result = stockDataService.syncStockList();
            
            log.info("[定时任务] 股票列表同步完成 - 总数: {}, 新增: {}, 更新: {}, 失败: {}, 耗时: {}ms", 
                result.getTotalCount(), result.getSavedCount(), 
                result.getUpdatedCount(), result.getFailedCount(), result.getCostTimeMs());
            
        } catch (Exception e) {
            log.error("[定时任务] 同步股票列表失败", e);
        }
    }

    // ==================== 历史价格同步 ====================

    /**
     * 每日收盘后同步最新股票数据
     * 每个交易日下午 16:00 执行
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void syncDailyStockData() {
        log.info("========== [定时任务] 开始执行每日股票数据同步 ==========");

        try {
            List<StockInfo> allStocks = stockDataService.findAllStocks();
            log.info("[定时任务] 共需同步 {} 支股票的数据", allStocks.size());

            // 并行处理每支股票的数据同步
            List<CompletableFuture<Void>> futures = allStocks.stream()
                .map(stock -> CompletableFuture.runAsync(() -> {
                    try {
                        syncStockData(stock.getCode());
                    } catch (Exception e) {
                        log.error("[定时任务] 同步股票 {} 数据失败", stock.getCode(), e);
                    }
                }))
                .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.info("[定时任务] 每日股票数据同步完成");
        } catch (Exception e) {
            log.error("[定时任务] 执行每日股票数据同步失败", e);
        }
    }

    /**
     * 全量同步所有股票历史数据
     * 每周日凌晨 2:00 执行
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void syncAllHistoricalData() {
        log.info("========== [定时任务] 开始执行全量股票历史数据同步 ==========");

        try {
            List<StockInfo> allStocks = stockDataService.findAllStocks();
            log.info("[定时任务] 共需同步 {} 支股票的历史数据", allStocks.size());

            LocalDate endDate = LocalDate.now().minusDays(1);
            LocalDate startDate = LocalDate.of(2005, 1, 1);

            for (StockInfo stock : allStocks) {
                try {
                    int count = stockDataService.syncHistoricalData(stock.getCode(), startDate, endDate);
                    log.debug("[定时任务] 股票 {}-{} 历史数据同步完成，共 {} 条", 
                             stock.getCode(), stock.getName(), count);
                } catch (Exception e) {
                    log.error("[定时任务] 同步股票 {} 历史数据失败", stock.getCode(), e);
                }
            }

            log.info("[定时任务] 全量股票历史数据同步完成");
        } catch (Exception e) {
            log.error("[定时任务] 执行全量股票历史数据同步失败", e);
        }
    }

    /**
     * 同步单支股票的数据
     */
    /**
     * 同步单只股票的数据 (增量同步)
     */
    private void syncStockData(String stockCode) {
        stockDataService.syncLatestPriceData(stockCode);
    }

    // ==================== 新闻同步 ====================

    /**
     * 定时采集股票新闻
     * 每小时执行一次
     * TODO: 实现后启用
     */
    @Scheduled(cron = "0 0 * * * *")
    public void collectHourlyNews() {
        log.debug("股票新闻采集功能待实现，跳过执行");
        // TODO: stockNewsService.collectStockNews();
    }
}