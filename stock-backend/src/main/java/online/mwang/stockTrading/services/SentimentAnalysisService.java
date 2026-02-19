package online.mwang.stockTrading.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.clients.SentimentClient;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 情感分析业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisService {

    private final SentimentClient sentimentClient;

    /**
     * 分析单条文本情感
     * @param text 待分析文本
     * @return 情感得分 (-1到1)
     */
    public double analyze(String text) {
        try {
            SentimentClient.SentimentResponse response = sentimentClient.analyze(text);
            return convertToScore(response.getLabel(), response.getScore());
        } catch (Exception e) {
            log.error("Failed to analyze sentiment: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 计算股票综合情感得分
     * @param stockCode 股票代码
     * @param newsItems 新闻列表
     * @return 情感得分 (-1到1)
     */
    public double calculateStockSentiment(String stockCode, List<Map<String, Object>> newsItems) {
        if (newsItems == null || newsItems.isEmpty()) {
            log.warn("No news available for stock: {}", stockCode);
            return 0.0;
        }

        try {
            List<SentimentClient.NewsItem> items = new ArrayList<>();
            for (Map<String, Object> news : newsItems) {
                SentimentClient.NewsItem item = new SentimentClient.NewsItem();
                item.setTitle((String) news.get("title"));
                item.setContent((String) news.get("content"));
                item.setUrl((String) news.get("url"));
                item.setPublishedAt((String) news.get("publishedAt"));
                items.add(item);
            }

            List<Map<String, Object>> results = sentimentClient.analyzeNews(items);
            return calculateWeightedScore(results);
            
        } catch (Exception e) {
            log.error("Failed to calculate sentiment for stock {}: {}", stockCode, e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取市场整体情绪
     * @param newsItems 新闻列表
     * @return 市场情绪结果
     */
    public MarketSentiment getMarketSentiment(List<Map<String, Object>> newsItems) {
        if (newsItems == null || newsItems.isEmpty()) {
            return MarketSentiment.neutral();
        }

        try {
            List<SentimentClient.NewsItem> items = new ArrayList<>();
            for (Map<String, Object> news : newsItems) {
                SentimentClient.NewsItem item = new SentimentClient.NewsItem();
                item.setTitle((String) news.get("title"));
                item.setContent((String) news.get("content"));
                items.add(item);
            }

            SentimentClient.MarketSentimentResponse response = 
                sentimentClient.analyzeMarketSentiment(items);

            return MarketSentiment.builder()
                .overall(response.getOverall())
                .score(response.getScore())
                .positiveCount(response.getPositiveCount())
                .neutralCount(response.getNeutralCount())
                .negativeCount(response.getNegativeCount())
                .totalCount(response.getTotalCount())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get market sentiment: {}", e.getMessage());
            return MarketSentiment.neutral();
        }
    }

    /**
     * 获取股票情感排名
     */
    public List<StockSentimentScore> getStockSentimentRanking(
            Map<String, List<Map<String, Object>>> stockNewsMap) {
        List<StockSentimentScore> scores = new ArrayList<>();
        
        for (Map.Entry<String, List<Map<String, Object>>> entry : stockNewsMap.entrySet()) {
            String stockCode = entry.getKey();
            List<Map<String, Object>> news = entry.getValue();
            
            double score = calculateStockSentiment(stockCode, news);
            
            scores.add(StockSentimentScore.builder()
                .stockCode(stockCode)
                .sentimentScore(score)
                .build());
        }
        
        scores.sort((a, b) -> Double.compare(b.getSentimentScore(), a.getSentimentScore()));
        return scores;
    }

    private double convertToScore(String label, Double score) {
        if (label == null) return 0.0;
        switch (label.toLowerCase()) {
            case "positive": return score != null ? score : 0.5;
            case "negative": return score != null ? -score : -0.5;
            case "neutral": default: return 0.0;
        }
    }

    @SuppressWarnings("unchecked")
    private double calculateWeightedScore(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) return 0.0;

        double totalWeight = 0.0, weightedSum = 0.0;
        for (Map<String, Object> result : results) {
            String sentiment = (String) result.get("sentiment");
            Double score = (Double) result.get("sentiment_score");
            Double confidence = (Double) result.get("sentiment_confidence");
            if (confidence == null) confidence = 0.5;
            double sentimentValue = convertToScore(sentiment, score);
            weightedSum += sentimentValue * confidence;
            totalWeight += confidence;
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 0.0;
    }

    @lombok.Data
    @lombok.Builder
    public static class MarketSentiment {
        private String overall;
        private Double score;
        private Integer positiveCount;
        private Integer neutralCount;
        private Integer negativeCount;
        private Integer totalCount;

        public static MarketSentiment neutral() {
            return MarketSentiment.builder()
                .overall("neutral").score(0.0)
                .positiveCount(0).neutralCount(0).negativeCount(0).totalCount(0)
                .build();
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class StockSentimentScore {
        private String stockCode;
        private Double sentimentScore;
    }
}
