package com.stock.databus.scheduled;

import com.stock.databus.collector.NewsCollector;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockNews;
import com.stock.databus.repository.StockRepository;
import com.stock.databus.service.NewsCleaningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 新闻数据采集调度器
 * 处理定时采集各类金融资讯并進行清洗
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsCollectionScheduler {

    private final NewsCollector newsCollector;
    private final NewsCleaningService newsCleaningService;
    private final StockRepository stockRepository;

    /**
     * 每15分鐘進行一次財經新聞採集
     * 滋選活躍股票進行新聞采集，避免過度請求第三方網站（防反爬蟲機制）
     * Cron: 每小時分鐘數15,30,45執行，避免與其他服務頻發衝突
     */
    @Scheduled(cron = "0 15,30,45 * * * ?")
    public void collectFinanceNewsPeriodically() {
        log.info("========== 開始執行定時新聞採集任務 ==========");
        
        try {
            // 獲取前N只熱門股票代碼（基於交易量或關注度）
            List<StockInfo> hotStocks = stockRepository.findTradableStocks(50); // 只取前50只熱門股票
            log.info("從數據庫獲取 {} 只交易股票進行新聞採集", hotStocks.size());
            
            int totalCollected = 0;
            int totalSaved = 0;
            
            for (StockInfo stock : hotStocks) {
                try {
                    log.debug("開始采購股票 {} 的新聞", stock.getCode());
                    
                    // 采集新聞
                    List<StockNews> rawNews = newsCollector.collectStockNews(stock.getCode());
                    log.info("股票 {} 采集原始新聞共 {} 條", stock.getCode(), rawNews.size());
                    
                    // 清洗新聞數據
                    List<StockNews> cleanedNews = newsCleaningService.cleanBatchNews(rawNews);
                    log.info("股票 {} 清洗後新聞共 {} 條", stock.getCode(), cleanedNews.size());
                    
                    // 保存清洗後的數據
                    if (!cleanedNews.isEmpty()) {
                        newsCollector.saveBatchNews(cleanedNews);
                        totalSaved += cleanedNews.size();
                        log.info("股票 {} 成功保存 {} 條新聞", stock.getCode(), cleanedNews.size());
                        
                        // 為避免被封鎖IP，對每個股票批量處理完後稍作停頓
                        Thread.sleep(3000);
                    }
                    
                    totalCollected += rawNews.size();
                    
                } catch (Exception e) {
                    log.error("处理股票 {} 新闻采購时出错", stock.getCode(), e);
                    // 繼續處理下一隻股票
                    continue;
                }
            }
            
            log.info("新聞採集任務完成，總計采集: {} 條，總計保存: {} 條", totalCollected, totalSaved);
            
        } catch (Exception e) {
            log.error("執行新聞採集任務時發生異常", e);
        }
    }

    /**
     * 每小時執行一次批量新聞處理任務
     * 執行批量數據清洗和驗證，對於之前批次中的遺留或錯誤進行補充處理
     */
    @Scheduled(cron = "0 0 2 * * ?") // 每天凌晨2點執行
    public void dailyNewsCleanupTask() {
        log.info("========== 開始執行日常新聞數據清理任務 ==========");
        
        try {
            // 執行數據完整性檢查、重複項清理等清理任務
            // 目前為止先寫明確保架構完整性
            
            log.info("日常新聞數據清理任務完成");
        } catch (Exception e) {
            log.error("執行日常清理任務時發生異常", e);
        }
    }

    /**
     * 批量處理新聞采集 (支持手動觸發)
     * 遥用時可通過API調用此方法
     */
    public void collectNewsForMultipleStocks(List<String> stockCodes) {
        log.info("開始為 {} 只票代碼批量採集新聞，共 {} 只", stockCodes.size(), stockCodes.size());
        
        // 並發處理多個股票的新聞采購，提高效率
        List<CompletableFuture<Void>> futures = stockCodes.stream()
            .map(code -> CompletableFuture.runAsync(() -> {
                try {
                    log.debug("開始为股票 {} 采集新闻", code);
                    List<StockNews> rawNews = newsCollector.collectStockNews(code);
                    
                    if (!rawNews.isEmpty()) {
                        List<StockNews> cleanedNews = newsCleaningService.cleanBatchNews(rawNews);
                        if (!cleanedNews.isEmpty()) {
                            newsCollector.saveBatchNews(cleanedNews);
                        }
                    }
                    
                    // 批量请求間延遲
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignore) {}
                    
                } catch (Exception e) {
                    log.error("批量采集股票 {} 新聞時出錯", code, e);
                }
            }))
            .collect(Collectors.toList());
        
        // 等待所有任務完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.info("完成批量新聞采購任務");
    }
    
    /**
     * 手動觸發新聞采集（特定股票）
     * 傥用來立即采集指定股票的新聞，例如當某只股票出現異動時
     */
    public void collectNewsForSingleStock(String stockCode) {
        log.info("開始為股票 {} 執行立即新聞採集", stockCode);
        
        try {
            List<StockNews> rawNews = newsCollector.collectStockNews(stockCode);
            log.info("股票 {} 采集到 {} 條原始新聞", stockCode, rawNews.size());
            
            if (!rawNews.isEmpty()) {
                List<StockNews> cleanedNews = newsCleaningService.cleanBatchNews(rawNews);
                if (!cleanedNews.isEmpty()) {
                    newsCollector.saveBatchNews(cleanedNews);
                    log.info("為股票 {} 成功保存 {} 條清洗後新聞", stockCode, cleanedNews.size());
                } else {
                    log.info("股票 {} 清洗後無有效新聞", stockCode);
                }
            }
        } catch (Exception e) {
            log.error("為股票 {} 採集新聞時出錯", stockCode, e);
        }
    }
}