package com.stock.dataCollector.listener;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.StockInfoRepository;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.dataCollector.service.StockDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.beans.factory.annotation.Value;
import java.util.List;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

/**
 * 应用启动后数据初始化监听器
 * 在应用完全启动后检查StockInfo表数据量，如果少于4000条则执行数据同步
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.startup.listener.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ApplicationStartupListener {

    private final StockDataService stockDataService;
    private final StockInfoRepository stockInfoRepository;
    private final PriceRepository priceRepository;

    @Value("${app.startup.history-sync.enabled:false}")
    private boolean historySyncEnabled;


    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 使用 @Async 不阻塞启动；再使用 CompletableFuture 将列表同步与历史同步放入后台执行
        // 这样可以避免阻塞主应用启动，同时保证先同步列表再同步历史数据的顺序依赖
        CompletableFuture.runAsync(() -> {
            log.info("========== 应用启动完成，开始后台数据初始化任务 ==========");
            
            try {
                // 1. 检查并同步股票列表
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
                log.error("后台数据初始化任务（列表同步）失败", e);
            }
    
            log.info("========== 数据初始化检查完成，准备开始历史数据同步 ==========");
    

            // 2. 同步所有股票的历史数据
            // 注意：这里是顺序执行，确保如果上方同步了新股票列表，这里能获取到最新的列表
            if (historySyncEnabled) {
                try {
                    syncAllStocksHistoryToMongo();
                } catch (Exception e) {
                    log.error("后台数据初始化任务（历史数据同步）失败", e);
                }
            } else {
                log.info("========== 历史数据同步开关已关闭，跳过同步 ==========");
            }

        });
    }

    /**
     * 批量同步所有股票历史价格到 MongoDB
     * 与测试方法 testSyncAllStocksHistoryToMongo 逻辑一致
     */
    private void syncAllStocksHistoryToMongo() {
        log.info("========== 开始批量同步所有股票历史价格 ==========");

        List<String> codes = stockInfoRepository.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            log.warn("数据库中无股票代码，跳过历史价格同步");
            return;
        }

        int totalStocks = codes.size();
        log.info("本次需要同步历史价格的股票总数: {}", totalStocks);

        int totalStocksWithData = 0;
        int totalRecords = 0;

        int index = 0;
        int skippedCount = 0;
        for (String code : codes) {
            index++;
            try {
                // 优化：仅当今天的数据不存在时才同步，支持每日启动更新
                if (priceRepository.existsByCodeAndDate(code, LocalDate.now())) {
                    log.info("股票 {} 已存在今日({})的历史数据，跳过同步", code, LocalDate.now());
                    skippedCount++;
                    continue;
                }

                List<StockPrice> prices = stockDataService.getHistoryPrices(code);

                if (prices == null || prices.isEmpty()) {
                    log.warn("股票 {} 无历史价格数据，跳过", code);
                    continue;
                }

                stockDataService.saveStockPrices(prices);
                totalStocksWithData++;
                totalRecords += prices.size();

                double percent = index * 100.0 / totalStocks;
                log.info("股票 {} 历史数据条数: {}，当前进度: {}/{} ({})",
                    code,
                    prices.size(),
                    index,
                    totalStocks,
                    String.format("%.2f%%", percent));
            } catch (Exception e) {
                log.error("同步股票 {} 历史价格失败: {}", code, e.getMessage(), e);
            }
        }

        log.info("========== 批量同步完成，成功写入 {} 只股票，跳过 {} 只已存在股票，共 {} 条历史价格记录 =========="
            , totalStocksWithData, skippedCount, totalRecords);
    }
}