package com.stock.dataCollector.listener;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 应用启动后数据初始化监听器
 * 在应用完全启动后检查StockInfo表数据量，如果少于4000条则执行数据同步
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final StockDataService stockDataService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("========== 应用启动完成，开始数据初始化检查 ==========");
        
        try {
            long currentCount = stockDataService.count();
            log.info("当前StockInfo表中的股票数据数量: {}", currentCount);
            
            if (currentCount < 4000) {
                log.warn("数据量不足4000条({}条)，开始执行数据同步...", currentCount);
                
                // 执行数据同步
                StockDataService.SyncResult result = stockDataService.syncStockList();
                
                log.info("数据同步结果: 总数={}, 新增={}, 更新={}, 失败={}, 耗时={}ms",
                    result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount(),
                    result.getFailedCount(), result.getCostTimeMs());
                    
                // 获取一些示例数据进行日志输出
                List<StockInfo> sampleStocks = stockDataService.findAllStocks();
                int printCount = Math.min(5, sampleStocks.size());
                for (int i = 0; i < printCount; i++) {
                    StockInfo stock = sampleStocks.get(i);
                    log.info("股票数据示例 - 代码: {}, 名称: {}, 市场: {}, 当前价格: {}, 涨跌幅: {}, " +
                            "涨跌额: {}, 总市值: {}, 换手率: {}, 量比: {}, 行业代码: {}",
                        stock.getCode(),
                        stock.getName(),
                        stock.getMarket(),
                        stock.getPrice(),
                        stock.getChangePercent(),
                        stock.getChangeAmount(),
                        stock.getTotalMarketValue(),
                        stock.getTurnoverRate(),
                        stock.getVolumeRatio(),
                        stock.getIndustryCode()
                    );
                }
                
            } else {
                log.info("数据量充足({}条)，跳过数据同步", currentCount);
            }
            
        } catch (Exception e) {
            log.error("数据初始化检查失败", e);
        }
        
        log.info("========== 数据初始化检查完成 ==========");
    }
}