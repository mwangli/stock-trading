package com.stock.dataCollector.scheduled;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 股票数据同步定时任务
 * 负责定时同步股票数据到MySQL和MongoDB
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockDataSyncScheduler {

    private final StockDataService stockDataService;

    /**
     * 每天凌晨1点同步股票列表到MySQL
     * cron表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void syncStockListToMySqlDaily() {
        log.info("========== [定时任务] 开始同步股票列表到MySQL ==========");
        
        try {
            StockDataService.SyncResult result = stockDataService.syncStockListToMySql();
            
            log.info("[定时任务] 股票列表同步完成 - 总数: {}, 新增: {}, 更新: {}, 失败: {}, 耗时: {}ms", 
                result.getTotalCount(), result.getSavedCount(), 
                result.getUpdatedCount(), result.getFailedCount(), result.getCostTimeMs());
            
        } catch (Exception e) {
            log.error("[定时任务] 同步股票列表到MySQL失败", e);
        }
    }

    /**
     * 每个交易日早上9点同步股票列表
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void syncStockList() {
        log.info("========== [定时任务] 开始同步股票列表 ==========");
        try {
            int count = stockDataService.fetchAndSaveStockList();
            log.info("[定时任务] 股票列表同步完成，共 {} 条", count);
        } catch (Exception e) {
            log.error("[定时任务] 同步股票列表失败", e);
        }
    }

    /**
     * 每日收盘后同步最新股票数据
     * 每个交易日下午 16:00 执行
     */
    @Scheduled(cron = "0 0 16 * * MON-FRI")
    public void syncDailyStockData() {
        log.info("========== [定时任务] 开始执行每日股票数据同步 ==========");

        try {
            List<StockInfo> allStocks = stockDataService.getAllStocks();
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

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            log.info("[定时任务] 每日股票数据同步完成");
        } catch (Exception e) {
            log.error("[定时任务] 执行每日股票数据同步失败", e);
        }
    }

    /**
     * 同步单支股票的数据
     */
    private void syncStockData(String stockCode) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(365);

        int count = stockDataService.syncHistoricalData(stockCode, startDate, endDate);
        log.debug("[定时任务] 股票 {} 同步完成，共 {} 条数据", stockCode, count);
    }

    /**
     * 全量同步所有股票历史数据
     * 每周日凌晨 2:00 执行
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void syncAllHistoricalData() {
        log.info("========== [定时任务] 开始执行全量股票历史数据同步 ==========");

        try {
            List<StockInfo> allStocks = stockDataService.getAllStocks();
            log.info("[定时任务] 共需同步 {} 支股票的历史数据", allStocks.size());

            LocalDate endDate = LocalDate.now().minusDays(1);
            LocalDate startDate = LocalDate.of(2020, 1, 1);

            for (StockInfo stock : allStocks) {
                try {
                    int count = stockDataService.syncHistoricalData(
                        stock.getCode(), 
                        startDate, 
                        endDate
                    );
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
}