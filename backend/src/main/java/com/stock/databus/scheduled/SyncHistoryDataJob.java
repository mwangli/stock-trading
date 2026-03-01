package com.stock.databus.scheduled;

import com.stock.databus.collector.StockCollector;
import com.stock.databus.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 历史数据同步定时任务
 * 定期从证券平台获取股票历史 K 线数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SyncHistoryDataJob {

    private final StockCollector stockCollector;
    private final StockRepository stockRepository;

    /**
     * 每个交易日获取历史数据
     * 周一到周五 18:00 执行（收盘后）
     */
    @Scheduled(cron = "0 0 18 * * MON-FRI")
    public void syncHistoryData() {
        log.info("开始执行历史数据同步任务");
        try {
            List<com.stock.databus.entity.StockInfo> stockList = stockRepository.findAll();
            log.info("共有 {} 只股票需要同步历史数据", stockList.size());

            int successCount = 0;
            int failCount = 0;

            for (com.stock.databus.entity.StockInfo stock : stockList) {
                try {
                    int count = stockCollector.collectHistoricalData(stock.getCode(), 100);
                    if (count > 0) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("股票 {} 历史数据同步失败", stock.getName(), e);
                    failCount++;
                }
            }

            log.info("历史数据同步完成，成功：{}，失败：{}", successCount, failCount);
        } catch (Exception e) {
            log.error("历史数据同步任务执行失败", e);
        }
    }
}
