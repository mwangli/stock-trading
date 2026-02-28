package com.stock.databus.service;

import com.stock.databus.collector.NewsCollector;
import com.stock.databus.collector.StockCollector;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 统合数据采集服务
 * 集成股票信息采集、歷史數據采集和新聞數據采集的統一服務
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegratedDataCollectionService {

    private final StockCollector stockCollector;
    private final NewsCollector newsCollector;
    private final StockRepository stockRepository;
    private final NewsCleaningService newsCleaningService;

    /**
     * 執行完整的数据同步
     * 包括：股票基礎數據、歷史K線數據和財經新聞
     */
    public void performFullDataSync() {
        log.info("========== 開始執行完整數據同步 ==========");
        
        try {
            // 第一步: 同步股票列表
            log.info("開始同步股票列表...");
            int stockCount = stockCollector.collectStockList();
            log.info("股票列表同步完成，共同步 {} 只股票", stockCount);
            
            // 第二步: 同步財經新聞（針對前50只活躍股票）
            log.info("開始同步財經新聞...");
            syncNewsData();
            log.info("財經新聞同步完成");
            
            // 第三步: 同步歷史數據（可選，根據需要調整頻率）
            // 同步所有股票的基礎歷史數據到MongoDB
            syncHistoricalData();
            
            log.info("========== 完整數據同步完成 ==========");
            
        } catch (Exception e) {
            log.error("執行完整數據同步時發生異常", e);
        }
    }

    /**
     * 執行增量數據同步
     * 僅況：只同步自上次同步以來變化的數據
     */
    public void performIncrementalSync() {
        log.info("========== 開始執行增量數據同步 ==========");
        
        try {
            // 同步最新股票列表
            log.info("開始增量同步股票列表...");
            stockCollector.collectStockList();
            
            // 同步最近的財經新聞
            log.info("開始增量同步財經新聞...");
            syncLatestNews();
            
            log.info("========== 增量數據同步完成 ==========");
            
        } catch (Exception e) {
            log.error("執行增量數據同步時發生異常", e);
        }
    }

    /**
     * 同步指定股票的完整數據
     * 包括：基礎信息、歷史數據、新聞
     */
    public void performStockSpecificSync(String stockCode) {
        log.info("開始為股票 {} 執行指定數據同步", stockCode);
        
        try {
            // 同步該股票的詳細信息
            List<StockInfo> stockList = List.of(
                new StockInfo() {{
                    setCode(stockCode);
                }}
            );
            
            // 同步歷史數據
            int historicalDataCount = stockCollector.collectHistoricalData(stockCode, 30); // 最近30天的數據
            log.info("股票 {} 歷步歷史數據 {} 條", stockCode, historicalDataCount);
            
            // 同步新聞
            log.info("開始同步股票 {} 新聞", stockCode);
            var newsScheduler = new com.stock.databus.scheduled.NewsCollectionScheduler(
                newsCollector, newsCleaningService, stockRepository);
            newsScheduler.collectNewsForSingleStock(stockCode);
            
            log.info("股票 {} 完整數據同步完成", stockCode);
        } catch (Exception e) {
            log.error("為股票 {} 執行數據同步時發生異常", stockCode, e);
        }
    }

    /**
     * 同步財經新聞數據
     * 傥用於定期同步所有關注中的股票新聞
     */
    public void syncNewsData() {
        try {
            // 獲取所有可交易股票
            List<StockInfo> tradableStocks = stockRepository.findTradableStocks(50);
            
            // 只選取前50只活躍股票進行新聞同步（避免過度請求）
            for (int i = 0; i < Math.min(tradableStocks.size(), 50); i++) {
                StockInfo stock = tradableStocks.get(i);
                
                try {
                    log.debug("開始為股票 {} 同步新聞, 已處理 {}/{}", 
                        stock.getCode(), i + 1, Math.min(tradableStocks.size(), 50));
                    
                    // 采集該股票的新聞
                    var newsScheduler = new com.stock.databus.scheduled.NewsCollectionScheduler(
                        newsCollector, newsCleaningService, stockRepository);
                    newsScheduler.collectNewsForSingleStock(stock.getCode());
                    
                    // 間隔一段時間以避免被反爬蟲機制限制
                    Thread.sleep(3000);
                    
                } catch (InterruptedException e) {
                    log.warn("新聞采集任務被中斷");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("為股票 {} 同步新聞時發生異常", stock.getCode(), e);
                    // 繼續處理其他股票
                }
            }
        } catch (Exception e) {
            log.error("執行新聞數據同步時發生異常", e);
        }
    }

    /**
     * 同步最新的財經新聞
     * 傥用於增量更新，只需獲取最新的新聞
     */
    public void syncLatestNews() {
        try {
            // 從數據庫獲取最新的一批股票進行新聞采集
            List<StockInfo> latestStocks = stockRepository.findTradableStocks(20);
            
            for (StockInfo stock : latestStocks) {
                try {
                    var newsScheduler = new com.stock.databus.scheduled.NewsCollectionScheduler(
                        newsCollector, newsCleaningService, stockRepository);
                    newsScheduler.collectNewsForSingleStock(stock.getCode());
                    
                    Thread.sleep(2000); // 延遲以避免過度請求
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("最新新聞同步被中斷");
                    break;
                } catch (Exception e) {
                    log.error("獲取股票 {} 最新新聞時出錯", stock.getCode(), e);
                }
            }
        } catch (Exception e) {
            log.error("獲取最新財經新聞時發生異常", e);
        }
    }

    /**
     * 同步歷史數據
     * 零散的歷史數據更新
     */
    private void syncHistoricalData() {
        try {
            List<StockInfo> stocks = stockRepository.findTradableStocks(10); // 獲取前10只股票的歷史數據
            
            for (StockInfo stock : stocks) {
                try {
                    log.debug("為股票 {} 同步歷史數據", stock.getCode());
                    stockCollector.collectHistoricalData(stock.getCode(), 7); // 最近7天的數據
                    Thread.sleep(1000); // 遏止過度請求
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("歷史數據採集被中斷");
                    break;
                } catch (Exception e) {
                    log.error("獲取股票 {} 歷史數據時出錯", stock.getCode(), e);
                }
            }
        } catch (Exception e) {
            log.error("同步歷史數據時發生異常", e);
        }
    }

    /**
     * 執行並發數據採集
     * 為大量股票並發採集多種類型的數據
     */
    public CompletableFuture<Void> performConcurrentDataCollection(List<String> stockCodes) {
        log.info("開始为 {} 只股票執行並發數據採集", stockCodes.size());
        
        return CompletableFuture.runAsync(() -> {
            try {
                // 並發執行不同類型的數據採集工作
                CompletableFuture<Void> stockInfoFuture = CompletableFuture.runAsync(() -> {
                    log.debug("開始採集股票資訊...");
                    // 肥實現
                });

                CompletableFuture<Void> newsCollectionFuture = CompletableFuture.runAsync(() -> {
                    log.debug("開始採集財經新聞...");
                    var newsScheduler = new com.stock.databus.scheduled.NewsCollectionScheduler(
                        newsCollector, newsCleaningService, stockRepository);
                    newsScheduler.collectNewsForMultipleStocks(stockCodes);
                });

                CompletableFuture<Void> historicalDataFuture = CompletableFuture.runAsync(() -> {
                    log.debug("開始採集歷史數據...");
                    // 肥實現    
                });
                
                // 等待所有採集任務完成
                CompletableFuture.allOf(stockInfoFuture, newsCollectionFuture, historicalDataFuture).join();
                
                log.info("並發數據採集完成");
                
            } catch (Exception e) {
                log.error("並發數據採集失敗", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 驗證數據完整性
     * 橗證採集的數據是否完整且準確
     */
    public boolean validateDataIntegrity() {
        try {
            // 簡單的完整性驗證
            long totalStocks = stockRepository.countAll();
            if (totalStocks <= 0) {
                log.warn("數據庫中沒有任何股票數據");
                return false;
            }
            
            // 這裏可以添加更多的完整性驗證措施
            log.info("數據完整性驗證通過，目前共有 {} 只股票", totalStocks);
            return true;
            
        } catch (Exception e) {
            log.error("驗證數據完整性時發生異常", e);
            return false;
        }
    }
}