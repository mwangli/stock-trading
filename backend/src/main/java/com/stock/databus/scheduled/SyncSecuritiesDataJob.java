package com.stock.databus.scheduled;

import com.stock.databus.collector.StockCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 证券平台数据同步定时任务
 * 定期从证券平台获取股票列表和价格数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncSecuritiesDataJob {

    private final StockCollector stockCollector;

    /**
     * 每个交易日同步股票数据
     * 周一到周五 09:00 执行
     */
    @Scheduled(cron = "0 0 9 * * MON-FRI")
    public void syncStockList() {
        log.info("开始执行证券平台股票列表同步任务");
        int count = stockCollector.collectStockList();
        log.info("股票列表同步完成，同步 {} 只股票", count);
    }

    /**
     * 每个交易日同步股票价格
     * 周一到周五 15:30 执行（收盘后）
     */
    @Scheduled(cron = "0 30 15 * * MON-FRI")
    public void syncStockPrices() {
        log.info("开始执行证券平台股票价格同步任务");
        // 这里可以添加批量同步所有股票价格的逻辑
        // 实际实现可以根据需要调整
    }
}
