package com.stock.strategyAnalysis.selector;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class StockSelector {

    public List<StockRecommendation> select(int limit) {
        log.info("开始选股, 目标数量: {}", limit);
        
        try {
            // 1. 获取所有可交易股票列表
            List<String> stockCodes = getTradableStocks();
            
            if (stockCodes.isEmpty()) {
                log.warn("无可交易股票");
                return Collections.emptyList();
            }
            
            // 2. 获取LSTM预测得分
            Map<String, Double> predictions = getLstmPredictions(stockCodes);
            
            // 3. 获取情感得分
            Map<String, Double> sentiments = getSentiments(stockCodes);
            
            // 4. 计算综合得分并排序
            List<ScoredStock> scoredStocks = calculateScores(predictions, sentiments);
            
            // 5. 返回Top N
            return scoredStocks.stream()
                    .sorted(Comparator.comparing(ScoredStock::getScore).reversed())
                    .limit(limit)
                    .map(this::toRecommendation)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("选股失败", e);
            return Collections.emptyList();
        }
    }

    private List<String> getTradableStocks() {
        // TODO: 从数据库获取可交易股票列表
        return Arrays.asList("600519", "000858", "601318", "600036", "000001");
    }

    private Map<String, Double> getLstmPredictions(List<String> stockCodes) {
        // TODO: 调用intelligence模块获取LSTM预测
        Map<String, Double> predictions = new HashMap<>();
        for (String code : stockCodes) {
            predictions.put(code, Math.random() * 2 - 1);
        }
        return predictions;
    }

    private Map<String, Double> getSentiments(List<String> stockCodes) {
        // TODO: 调用intelligence模块获取情感得分
        Map<String, Double> sentiments = new HashMap<>();
        for (String code : stockCodes) {
            sentiments.put(code, Math.random() * 2 - 1);
        }
        return sentiments;
    }

    private List<ScoredStock> calculateScores(Map<String, Double> predictions, Map<String, Double> sentiments) {
        List<ScoredStock> scoredStocks = new ArrayList<>();
        
        Set<String> allCodes = new HashSet<>();
        allCodes.addAll(predictions.keySet());
        allCodes.addAll(sentiments.keySet());
        
        for (String code : allCodes) {
            double lstmScore = predictions.getOrDefault(code, 0.0);
            double sentimentScore = sentiments.getOrDefault(code, 0.0);
            
            // Score = LSTM×0.6 + Sentiment×0.4
            double score = lstmScore * 0.6 + sentimentScore * 0.4;
            
            ScoredStock stock = new ScoredStock();
            stock.setStockCode(code);
            stock.setLstmScore(lstmScore);
            stock.setSentimentScore(sentimentScore);
            stock.setScore(score);
            
            scoredStocks.add(stock);
        }
        
        return scoredStocks;
    }

    private StockRecommendation toRecommendation(ScoredStock stock) {
        return StockRecommendation.builder()
                .stockCode(stock.getStockCode())
                .score(stock.getScore())
                .lstmScore(stock.getLstmScore())
                .sentimentScore(stock.getSentimentScore())
                .build();
    }

    @Data
    private static class ScoredStock {
        private String stockCode;
        private double lstmScore;
        private double sentimentScore;
        private double score;
    }

    @Data
    @lombok.Builder
    public static class StockRecommendation {
        private String stockCode;
        private double score;
        private double lstmScore;
        private double sentimentScore;
    }
}
