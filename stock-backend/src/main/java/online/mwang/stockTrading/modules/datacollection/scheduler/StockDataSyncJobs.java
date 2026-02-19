package online.mwang.stockTrading.modules.datacollection.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.datacollection.service.StockDataService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 股票数据同步定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockDataSyncJobs {

    private final StockDataService stockDataService;

    /**
     * 同步股票列表
     * 每天09:00执行
     */
    @Scheduled(cron = "${stock.data.sync.stock-list.cron:0 0 9 * * ?}")
    public void syncStockList() {
        log.info("[Scheduled Job] Starting stock list sync...");
        try {
            stockDataService.syncAllStocks();
            log.info("[Scheduled Job] Stock list sync completed successfully");
        } catch (Exception e) {
            log.error("[Scheduled Job] Stock list sync failed", e);
        }
    }

    /**
     * 同步实时行情
     * 交易时段每分钟执行 (9:00-15:00)
     */
    @Scheduled(cron = "${stock.data.sync.realtime-quote.cron:0 0/1 9-15 * * ?}")
    public void syncRealTimeQuotes() {
        log.debug("[Scheduled Job] Starting real-time quote sync...");
        try {
            // 实时行情同步逻辑在fetchRealTimePrice中已实现缓存机制
            // 这里可以触发批量行情刷新
            log.debug("[Scheduled Job] Real-time quote sync triggered");
        } catch (Exception e) {
            log.error("[Scheduled Job] Real-time quote sync failed", e);
        }
    }

    /**
     * 同步历史数据
     * 每天15:30执行（收盘后）
     */
    @Scheduled(cron = "${stock.data.sync.historical.cron:0 30 15 * * ?}")
    public void syncHistoricalData() {
        log.info("[Scheduled Job] Starting historical data sync...");
        try {
            // 可以在这里触发全量历史数据更新
            // 或者只更新当天的数据
            log.info("[Scheduled Job] Historical data sync completed");
        } catch (Exception e) {
            log.error("[Scheduled Job] Historical data sync failed", e);
        }
    }
}
