package com.stock.databus.service;

import com.stock.databus.collector.NewsCollector;
import com.stock.databus.entity.StockNews;
import com.stock.databus.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 新闻数据采集功能测试类
 * 测试并演示新聞數據采集和清洗功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewsDataTestService implements CommandLineRunner {

    private final NewsCollector newsCollector;
    private final NewsCleaningService newsCleaningService;
    private final NewsRepository newsRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("========== 測試新聞采集和清洗功能 ==========");
        
        try {
            // 測試数据
            String testStockCode = "000001";  // 平安银行
            
            // 測試新闻对象手动创建
            StockNews testNews = createTestNewsObject(testStockCode);
            log.info("創建測試新聞: {}", testNews.getTitle());
            
            // 驗證清洗前的數據狀態
            log.info("清洗前 - 標題: '{}', 內容: '{}'", testNews.getTitle(), testNews.getContent().length() > 50 ? testNews.getContent().substring(0, 50) + "..." : testNews.getContent());
            
            // 執行數據清洗
            StockNews cleanedNews = newsCleaningService.cleanNewsData(testNews);
            log.info("清洗後 - 標題: '{}', 內容: '{}'", cleanedNews.getTitle(), cleanedNews.getContent().length() > 50 ? cleanedNews.getContent().substring(0, 50) + "..." : cleanedNews.getContent());
            
            // 模擬新聞采集
            log.info("模擬采集股票 {} 的新聞", testStockCode);
            simulateNewsCollection(testStockCode);
            
            log.info("========== 新聞數據測試完成 ==========");
        } catch (Exception e) {
            log.error("新聞數據測試發生異常", e);
        }
    }

    /**
     * 創建測試新聞實例
     */
    private StockNews createTestNewsObject(String stockCode) {
        StockNews news = new StockNews();
        news.setStockCode(stockCode);
        news.setTitle("股票代碼" + stockCode + "發布業績公告！公司盈利超預期...");
        news.setContent("根據公司發布的業績報告，該公司在本季度實現盈利增長30%，大幅超出行業平均水平。市場分析認為，在當前經濟形勢下，該公司的表現十分優秀...");
        news.setSource("測試新聞源");
        news.setUrl("http://example.com/news/test-" + stockCode);
        news.setPubTime("2026-02-28 15:30:00");
        news.setCreateTime(LocalDateTime.now());
        return news;
    }

    /**
     * 模擬新聞采集過程
     */
    private void simulateNewsCollection(String stockCode) {
        log.info("--- 開始模擬采集股票 {} 的新聞 ---", stockCode);
        
        // 創建帶有污染數據的新聞項目（測試清洗功能）
        StockNews pollutedNews = new StockNews();
        pollutedNews.setStockCode(stockCode);
        pollutedNews.setTitle("   測試標題!!!(重複內容)...   ");
        pollutedNews.setContent("這是測試內容！！！！！內容包含垃圾數據~~~~~~~~~");
        pollutedNews.setSource(" 測試來源 ");
        pollutedNews.setUrl("//invalid-url-format");
        pollutedNews.setPubTime("2026-02-28 15:30");
        pollutedNews.setCreateTime(LocalDateTime.now());

        log.info("清理前 - 標題: '{}', 内容: '{}', URL: '{}'", 
            pollutedNews.getTitle(), 
            pollutedNews.getContent(),
            pollutedNews.getUrl());
        
        // 執行深度清洗
        StockNews cleanedNews = newsCleaningService.cleanNewsData(pollutedNews);
        
        log.info("清理後 - 標題: '{}', 内容: '{}', URL: '{}'", 
            cleanedNews.getTitle(), 
            cleanedNews.getContent().length() > 50 
                ? cleanedNews.getContent().substring(0, 50) + "..." 
                : cleanedNews.getContent(),
            cleanedNews.getUrl());
        
        // 保存清洗後的數據到數據庫
        try {
            newsRepository.save(cleanedNews);
            log.info("已保存清洗後的新聞到數據庫");
            
            // 模擬批量操作
            List<StockNews> batchNews = Arrays.asList(
                createTestNewsObject("600000"),
                createTestNewsObject("600036"),
                createTestNewsObject("000858")
            );
            
            // 渲染批量清洗
            List<StockNews> cleanedBatch = newsCleaningService.cleanBatchNews(batchNews);
            log.info("批量清洗完成，原始 {} 條，清洗後 {} 條", batchNews.size(), cleanedBatch.size());
            
        } catch (Exception e) {
            log.error("保存新聞數據時出錯", e);
        }
        
        log.info("--- 完成股票 {} 的新聞模擬采集 ---", stockCode);
    }

    /**
     * 執行新聞采集測試
     */
    public void runNewsCollectionTest() {
        String[] testStocks = {"000001", "600000", "000858"};
        
        for (String stockCode : testStocks) {
            try {
                log.info("開始測試采集股票 {} 的新聞", stockCode);
                
                // 如果系統中有可用的新聞采集功能，這兒會測試采集
                List<StockNews> collectedNews = newsCollector.collectStockNews(stockCode);
                log.info("為股票 {} 采集到 {} 條新聞", stockCode, collectedNews.size());
                
                if (!collectedNews.isEmpty()) {
                    log.info("對新聞數據進行批量清洗");
                    List<StockNews> cleanedBatch = newsCleaningService.cleanBatchNews(collectedNews);
                    log.info("清洗後獲得 {} 條有效新聞", cleanedBatch.size());
                    
                    // 模擬保存
                    if (!cleanedBatch.isEmpty()) {
                        newsCollector.saveBatchNews(cleanedBatch);
                        log.info("新聞數據已保存到數據庫");
                    }
                }
                
            } catch (Exception e) {
                log.error("測試股票 {} 的新聞采集時出錯", stockCode, e);
            }
        }
    }
}