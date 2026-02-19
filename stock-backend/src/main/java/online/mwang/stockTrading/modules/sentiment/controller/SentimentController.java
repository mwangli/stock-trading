package online.mwang.stockTrading.modules.sentiment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.core.dto.Response;
import online.mwang.stockTrading.modules.sentiment.service.SentimentAnalysisService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 情感分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/sentiment")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentAnalysisService sentimentService;

    /**
     * 分析单条文本情感
     */
    @PostMapping("/analyze")
    public Response<Double> analyze(@RequestBody TextRequest request) {
        log.info("Analyzing sentiment for text: {}", 
            request.getText() != null ? request.getText().substring(0, Math.min(50, request.getText().length())) + "..." : "null");
        
        double score = sentimentService.analyze(request.getText());
        return Response.success(score);
    }

    /**
     * 计算股票情感得分
     */
    @PostMapping("/stock/{stockCode}")
    public Response<Double> getStockSentiment(
            @PathVariable String stockCode,
            @RequestBody List<Map<String, Object>> newsItems) {
        log.info("Calculating sentiment for stock: {}", stockCode);
        
        double score = sentimentService.calculateStockSentiment(stockCode, newsItems);
        return Response.success(score);
    }

    /**
     * 获取市场整体情绪
     */
    @PostMapping("/market")
    public Response<SentimentAnalysisService.MarketSentiment> getMarketSentiment(
            @RequestBody List<Map<String, Object>> newsItems) {
        log.info("Analyzing market sentiment with {} news items", newsItems != null ? newsItems.size() : 0);
        
        SentimentAnalysisService.MarketSentiment sentiment = sentimentService.getMarketSentiment(newsItems);
        return Response.success(sentiment);
    }

    /**
     * 获取股票情感排名
     */
    @PostMapping("/ranking")
    public Response<List<SentimentAnalysisService.StockSentimentScore>> getRanking(
            @RequestBody Map<String, List<Map<String, Object>>> stockNewsMap) {
        log.info("Calculating sentiment ranking for {} stocks", stockNewsMap != null ? stockNewsMap.size() : 0);
        
        List<SentimentAnalysisService.StockSentimentScore> ranking = 
            sentimentService.getStockSentimentRanking(stockNewsMap);
        return Response.success(ranking);
    }

    // ==================== Request DTOs ====================

    @lombok.Data
    public static class TextRequest {
        private String text;
    }
}
