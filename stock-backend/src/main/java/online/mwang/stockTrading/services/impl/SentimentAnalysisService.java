package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.entities.StockSentiment;
import online.mwang.stockTrading.repositories.StockSentimentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * V3.0 情感分析业务服务
 * 从MySQL数据库读取Python写入的情感分析结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentAnalysisService {

    private final StockSentimentRepository stockSentimentRepository;

    /**
     * 获取股票今日情感得分
     * 从数据库读取Python写入的情感分析结果
     * @param stockCode 股票代码
     * @return 情感得分 (-1到1)
     */
    public double analyze(String stockCode) {
        try {
            LocalDate today = LocalDate.now();
            StockSentiment sentiment = stockSentimentRepository.findByStockCodeAndDate(stockCode, today);
            if (sentiment != null && sentiment.getSentimentScore() != null) {
                return sentiment.getSentimentScore();
            }
            // 如果今日无数据，尝试获取最近的数据
            List<StockSentiment> recentData = stockSentimentRepository.findByDate(today.minusDays(1));
            if (!recentData.isEmpty()) {
                StockSentiment latest = recentData.stream()
                    .filter(s -> s.getStockCode().equals(stockCode))
                    .findFirst()
                    .orElse(null);
                if (latest != null && latest.getSentimentScore() != null) {
                    log.info("Using previous day's sentiment for stock {}: {}", stockCode, latest.getSentimentScore());
                    return latest.getSentimentScore();
                }
            }
            log.warn("No sentiment data found for stock: {}", stockCode);
            return 0.0;
        } catch (Exception e) {
            log.error("Failed to get sentiment for stock {}: {}", stockCode, e.getMessage());
            return 0.0;
        }
    }

    /**
     * 获取所有股票今日情感得分列表
     * @return 情感得分列表
     */
    public List<StockSentiment> getTodaySentiments() {
        try {
            LocalDate today = LocalDate.now();
            return stockSentimentRepository.findByDate(today);
        } catch (Exception e) {
            log.error("Failed to get today's sentiments: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取股票情感排名
     * @param limit 返回前N只股票
     * @return 股票情感得分排名
     */
    public List<StockSentimentScore> getStockSentimentRanking(int limit) {
        try {
            LocalDate today = LocalDate.now();
            List<StockSentiment> sentiments = stockSentimentRepository.findTopByDate(today, limit);
            
            return sentiments.stream()
                .map(s -> StockSentimentScore.builder()
                    .stockCode(s.getStockCode())
                    .sentimentScore(s.getSentimentScore() != null ? s.getSentimentScore() : 0.0)
                    .positiveRatio(s.getPositiveRatio())
                    .negativeRatio(s.getNegativeRatio())
                    .newsCount(s.getNewsCount())
                    .build())
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.error("Failed to get sentiment ranking: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取市场整体情绪
     * 根据今日所有股票情感数据统计市场情绪
     * @return 市场情绪结果
     */
    public MarketSentiment getMarketSentiment() {
        try {
            LocalDate today = LocalDate.now();
            List<StockSentiment> sentiments = stockSentimentRepository.findByDate(today);
            
            if (sentiments.isEmpty()) {
                return MarketSentiment.neutral();
            }
            
            int positiveCount = 0;
            int negativeCount = 0;
            int neutralCount = 0;
            double totalScore = 0.0;
            
            for (StockSentiment s : sentiments) {
                Double score = s.getSentimentScore();
                if (score == null) score = 0.0;
                totalScore += score;
                
                if (score > 0.1) {
                    positiveCount++;
                } else if (score < -0.1) {
                    negativeCount++;
                } else {
                    neutralCount++;
                }
            }
            
            int totalCount = sentiments.size();
            double avgScore = totalScore / totalCount;
            
            String overall;
            if (avgScore > 0.1) {
                overall = "positive";
            } else if (avgScore < -0.1) {
                overall = "negative";
            } else {
                overall = "neutral";
            }
            
            return MarketSentiment.builder()
                .overall(overall)
                .score(avgScore)
                .positiveCount(positiveCount)
                .neutralCount(neutralCount)
                .negativeCount(negativeCount)
                .totalCount(totalCount)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get market sentiment: {}", e.getMessage());
            return MarketSentiment.neutral();
        }
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
        private Double positiveRatio;
        private Double negativeRatio;
        private Integer newsCount;
    }
}
