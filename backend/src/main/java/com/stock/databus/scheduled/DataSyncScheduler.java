package com.stock.databus.scheduled;

import com.stock.databus.collector.StockCollector;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据同步定时任务调度器
 * 处理股票基础信息和历史K线数据的定时同步
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSyncScheduler {

    private final StockCollector stockCollector;
    private final StockRepository stockRepository;

    /**
     * 每小时同步一次基础股票信息到MySQL数据库
     * Cron表达式: 每小时的第0分钟触发 (例如: 10:00, 11:00, 12:00...)
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void syncBasicStockInfo() {
        log.info("========== 开始同步基础股票信息 ==========");
        
        try {
            // 采集并同步所有股票列表到MySQL
            int syncCount = stockCollector.collectStockList();
            log.info("同步基础股票信息完成，共采集 {} 只股票", syncCount);
            
        } catch (Exception e) {
            log.error("同步基础股票信息失败", e);
        }
    }

    /**
     * 每天凌晨2点同步所有股票最近3年的K线数据到MongoDB
     * Cron表达式: 每天2点0分0秒执行
     * 
     * 注意：由于Tushare API频率限制，这里采用分批处理方式
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void syncHistoricalKlineData() {
        log.info("========== 开始同步历史K线数据（最近3年）==========");
        
        try {
            // 获取所有可交易股票
            List<StockInfo> tradableStocks = stockRepository.findTradableStocks(5000);
            log.info("获取到 {} 只可交易股票", tradableStocks.size());
            
            // 计算3年前的日期
            int days = 3 * 365; // 近似3年
            
            int successCount = 0;
            int failureCount = 0;
            
            for (int i = 0; i < tradableStocks.size(); i++) {
                StockInfo stock = tradableStocks.get(i);
                
                try {
                    log.info("正在同步股票 {} 的历史数据 ({}/{})", 
                            stock.getCode(), i + 1, tradableStocks.size());
                    
                    // 同步指定股票最近3年的K线数据
                    int klineCount = stockCollector.collectHistoricalData(stock.getCode(), days);
                    
                    log.info("股票 {} 历史数据同步完成，获取 {} 条K线记录", 
                            stock.getCode(), klineCount);
                    
                    successCount++;
                    
                    // 为了避免API频率限制，每处理一只股票后暂停
                    Thread.sleep(3000); // 休眠3秒
                    
                } catch (Exception e) {
                    log.error("同步股票 {} 的历史数据失败: {}", stock.getCode(), e.getMessage());
                    failureCount++;
                    
                    // 遇到错误也暂停一下避免连续失败影响API调用
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            log.info("历史K线数据同步完成，成功: {}，失败: {}", successCount, failureCount);
            
        } catch (Exception e) {
            log.error("同步历史K线数据任务失败", e);
        }
    }
    
    /**
     * 重载版本：用于手动触发同步指定股票的历史数据（通过服务调用）
     * 适合在应用启动或特定条件下同步特定股票
     */
    public void syncHistoricalDataForStock(String stockCode, int days) {
        try {
            log.info("开始手动同步股票 {} 最近 {} 天历史数据", stockCode, days);
            int count = stockCollector.collectHistoricalData(stockCode, days);
            log.info("股票 {} 历史数据同步完成，共 {} 条", stockCode, count);
        } catch (Exception e) {
            log.error("手动同步股票 {} 历史数据失败", stockCode, e);
        }
    }
}