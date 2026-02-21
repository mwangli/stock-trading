package online.mwang.stockTrading.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.dto.Response;
import online.mwang.stockTrading.services.impl.SentimentAnalysisService;
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
     * 获取股票情感得分 (从MySQL数据库读取)
     */
    @GetMapping("/stock/{stockCode}")
    public Response<Double> getStockSentiment(@PathVariable String stockCode) {
        log.info("Getting sentiment for stock: {}", stockCode);
        
        double score = sentimentService.analyze(stockCode);
        return Response.success(score);
    }

    /**
     * 获取市场整体情绪 (从MySQL数据库读取)
     */
    @GetMapping("/market")
    public Response<SentimentAnalysisService.MarketSentiment> getMarketSentiment() {
        log.info("Getting market sentiment");
        
        SentimentAnalysisService.MarketSentiment sentiment = sentimentService.getMarketSentiment();
        return Response.success(sentiment);
    }

    /**
     * 获取股票情感排名
     */
    @GetMapping("/ranking")
    public Response<List<SentimentAnalysisService.StockSentimentScore>> getRanking(
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting sentiment ranking with limit: {}", limit);
        
        List<SentimentAnalysisService.StockSentimentScore> ranking = 
            sentimentService.getStockSentimentRanking(limit);
        return Response.success(ranking);
    }

    // ==================== Request DTOs ====================

    @lombok.Data
    public static class TextRequest {
        private String text;
    }
}
