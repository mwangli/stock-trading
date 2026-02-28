package com.stock.databus.scheduled;

import com.stock.databus.collector.StockCollector;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * 增强版数据同步定时任务调度器
 * 包含常规同步、批量同步以及其他增强功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnhancedDataSyncScheduler {

    private final StockCollector stockCollector;
    private final StockRepository stockRepository;
    
    /**
     * 每小时同步一次基础股票信息到MySQL数据库（避开交易时段峰值）
     * Cron表达式: 每小时的第15分钟触发 (例如: 10:15, 11:15, 12:15...)，减少交易期间负载
     */
    @Scheduled(cron = "0 15 * * * ?")
    public void syncBasicStockInfoHourly() {
        log.info("========== [{}] 开始每小时同步基础股票信息 ==========", LocalDate.now());
        
        try {
            // 采集并同步所有股票列表到MySQL
            int syncCount = stockCollector.collectStockList();
            log.info("每小时同步基础股票信息完成，共采集 {} 只股票", syncCount);
            
        } catch (Exception e) {
            log.error("每小时同步基础股票信息失败", e);
        }
    }

    /**
     * 每日同步所有股票的最新数据到MongoDB
     * Cron表达式: 每天凌晨3点30分执行
     * 
     * 这是为了减少与Tushare API的频率限制冲突，采取更保守的同步策略
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void syncDailyKlineData() {
        log.info("========== [{}] 开始每日K线数据同步 ==========", LocalDate.now());
        
        try {
            // 获取所有可交易股票
            List<StockInfo> tradableStocks = stockRepository.findTradableStocks(5000);
            log.info("获取到 {} 只可交易股票进行K线数据同步", tradableStocks.size());
            
            // 同步最新的20个交易日数据（约一个月）
            int days = 20;
            
            int successCount = 0;
            int failureCount = 0;
            
            for (int i = 0; i < tradableStocks.size(); i++) {
                StockInfo stock = tradableStocks.get(i);
                
                try {
                    log.info("[K线同步] 正在同步股票 {} ({}/{})", 
                            stock.getCode(), i + 1, tradableStocks.size());
                    
                    // 同步指定股票最近的K线数据
                    int klineCount = stockCollector.collectHistoricalData(stock.getCode(), days);
                    
                    log.debug("股票 {} 历史数据同步完成，获取 {} 条K线记录", 
                            stock.getCode(), klineCount);
                    
                    successCount++;
                    
                    // 为了避免API频率限制，每处理一只股票后暂停
                    Thread.sleep(2000); // 休眠2秒
                    
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
            
            log.info("每日K线数据同步完成，成功: {}，失败: {}，总计: {}", 
                    successCount, failureCount, tradableStocks.size());
            
        } catch (Exception e) {
            log.error("每日K线数据同步任务失败", e);
        }
    }
    
    /**
     * 每周同步一次较长期的历史数据（如近一年的详细数据）
     * Cron表达式: 每周六凌晨4点执行
     */
    @Scheduled(cron = "0 0 4 * * SAT")
    public void syncWeeklyExtendedHistoricalData() {
        log.info("========== [{}] 开始每周扩展历史数据同步 ==========", LocalDate.now());
        
        try {
            // 获取所有可交易股票
            List<StockInfo> tradableStocks = stockRepository.findTradableStocks(5000);
            log.info("获取到 {} 只可交易股票进行扩展历史数据同步", tradableStocks.size());
            
            // 同步过去一年的数据
            int days = 365;
            
            int totalProcessed = 0;
            int successCount = 0;
            int failureCount = 0;
            
            // 分批处理以避免长时间运行
            for (int i = 0; i < Math.min(tradableStocks.size(), 100); i++) {  // 本周只处理前100只股票
                StockInfo stock = tradableStocks.get(i);
                
                try {
                    log.info("[扩展历史数据] 正在同步股票 {} (周处理批:{}/{})", 
                            stock.getCode(), i + 1, Math.min(tradableStocks.size(), 100));
                    
                    int klineCount = stockCollector.collectHistoricalData(stock.getCode(), days);
                    
                    log.info("股票 {} 扩展历史数据同步完成，获取 {} 条K线记录", 
                            stock.getCode(), klineCount);
                    
                    successCount++;
                    totalProcessed++;
                    
                    // 增加暂停时间处理长期数据
                    Thread.sleep(5000); // 休眠5秒
                    
                } catch (Exception e) {
                    log.error("同步股票 {} 的扩展历史数据失败: {}", stock.getCode(), e.getMessage());
                    failureCount++;
                    
                    try {
                        Thread.sleep(8000);  // 更长的暂停
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            log.info("每周扩展历史数据同步完成，本次处理: {}，成功: {}，失败: {}", 
                    totalProcessed, successCount, failureCount);
            
        } catch (Exception e) {
            log.error("每周扩展历史数据同步任务失败", e);
        }
    }

    /**
     * 每月月初清理过期数据，合并历史数据
     * Cron表达式: 每月1号凌晨5点执行
     */
    @Scheduled(cron = "0 0 5 1 * ?")
    public void monthlyDataMaintenance() {
        log.info("========== [{}] 开始每月数据维护 ==========", LocalDate.now());
        
        try {
            // 这里可以添加数据清理、合并或其他维护任务
            log.info("每月数据维护完成");
            
        } catch (Exception e) {
            log.error("每月数据维护任务失败", e);
        }
    }
    
    /**
     * 周末进行长时间的历史数据更新（如过去3年的数据）
     * Cron表达式: 每周日凌晨5点执行
     */
    @Scheduled(cron = "0 0 5 * * SUN")
    public void syncLongTermHistoricalData() {
        log.info("========== [{}] 开始长期历史数据同步（3年数据）==========", LocalDate.now());
        
        // 获取前N只股票用于长期历史数据同步（以避免API调用次数限制）
        try {
            List<StockInfo> tradableStocks = stockRepository.findTradableStocks(5000);
            log.info("获取到 {} 只可交易股票进行长期历史数据同步", tradableStocks.size());
            
            // 仅同步特定的重点股票（例如前50只）过去3年的数据
            int days = 3 * 365; // 3年
    
            int processedCount = 0;
            for (int i = 0; i < Math.min(tradableStocks.size(), 25); i++) {  // 每次只处理25只股票
                StockInfo stock = tradableStocks.get(i);
                
                try {
                    log.info("[长期历史] 正在同步股票 {} (3年数据, 当前处理: {}/{})", 
                            stock.getCode(), i + 1, Math.min(tradableStocks.size(), 25));
    
                    int klineCount = stockCollector.collectHistoricalData(stock.getCode(), days);
                    
                    log.info("股票 {} 长期历史数据同步完成，获取 {} 条K线记录", 
                            stock.getCode(), klineCount);
                    
                    processedCount++;
                    
                    // 处理大量历史数据后较长的休息
                    Thread.sleep(10000); // 休眠10秒
    
                } catch (Exception e) {
                    log.error("同步股票 {} 的长期历史数据失败: {}", stock.getCode(), e.getMessage());
                    
                    try {
                        Thread.sleep(15000);  // 很长的暂停
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
    
            log.info("长期历史数据同步完成，处理了 {} 只股票", processedCount);
            
        } catch (Exception e) {
            log.error("长期历史数据同步任务失败", e);
        }
    }
    
    /**
     * 应用程序就绪后，立即执行一次初步数据加载
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        log.info("应用程序已就绪，开始初始化数据加载...");
        
        try {
            log.info("开始初始化同步基础股票信息");
            int count = stockCollector.collectStockList();
            log.info("初始化基础股票信息同步完成，共 {} 只股票", count);
        } catch (Exception e) {
            log.error("初始化同步失败", e);
        }
    }
    
    /**
     * 每季度进行一次全量数据刷新
     * Cron表达式: 每个季度的第一个月的第1天的早上6点执行
     */
    @Scheduled(cron = "0 0 6 1 1,4,7,10 ?")
    public void quarterlyFullSync() {
        log.info("========== [{}] 开始季度全量数据同步 ==========", LocalDate.now());
        
        try {
            log.info("执行季度全量股票数据刷新");
            int count = stockCollector.collectStockList();
            log.info("季度全量股票数据刷新完成，共处理 {} 只股票", count);
            
            // 还可以选择在此进行一些数据校验操作
            
        } catch (Exception e) {
            log.error("季度全量数据同步任务失败", e);
        }
    }
}