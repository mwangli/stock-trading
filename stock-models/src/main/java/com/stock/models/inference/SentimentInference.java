package com.stock.models.inference;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SentimentInference {

    private boolean isLoaded = false;

    public void loadModel() {
        try {
            // TODO: 加载DJL BERT模型
            log.info("情感分析模型加载中...");
            this.isLoaded = true;
            log.info("情感分析模型加载成功");
        } catch (Exception e) {
            log.error("加载情感分析模型失败", e);
            isLoaded = false;
        }
    }

    public SentimentResult analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return SentimentResult.neutral();
        }

        if (!isLoaded) {
            log.warn("模型未加载, 返回默认情感");
            return SentimentResult.neutral();
        }

        try {
            // TODO: 实现DJL BERT推理
            // 1. 分词
            // 2. 创建输入张量
            // 3. 推理
            // 4. 解析结果

            // 临时返回模拟结果
            return analyzeSimple(text);
        } catch (Exception e) {
            log.error("情感分析失败", e);
            return SentimentResult.neutral();
        }
    }

    private SentimentResult analyzeSimple(String text) {
        String lowerText = text.toLowerCase();
        
        // 简单的关键词匹配
        int positiveCount = 0;
        int negativeCount = 0;
        
        String[] positiveWords = {"增长", "盈利", "上涨", "突破", "利好", "推荐", "买入", "收益", "业绩", "增长"};
        String[] negativeWords = {"下跌", "亏损", "风险", "减持", "利空", "卖出", "警告", "亏损", "业绩下滑", "风险"};
        
        for (String word : positiveWords) {
            if (lowerText.contains(word)) positiveCount++;
        }
        for (String word : negativeWords) {
            if (lowerText.contains(word)) negativeCount++;
        }
        
        SentimentResult result = new SentimentResult();
        if (positiveCount > negativeCount) {
            result.setLabel("positive");
            result.setScore(0.7f);
            result.setConfidence(0.7f);
        } else if (negativeCount > positiveCount) {
            result.setLabel("negative");
            result.setScore(-0.7f);
            result.setConfidence(0.7f);
        } else {
            result.setLabel("neutral");
            result.setScore(0.0f);
            result.setConfidence(0.5f);
        }
        
        return result;
    }

    public Map<String, Object> analyzeWithDetails(String text) {
        SentimentResult result = analyze(text);
        
        Map<String, Object> response = new HashMap<>();
        response.put("label", result.getLabel());
        response.put("score", result.getScore());
        response.put("confidence", result.getConfidence());
        
        Map<String, Float> probabilities = new HashMap<>();
        float base = Math.abs(result.getScore());
        probabilities.put("positive", "positive".equals(result.getLabel()) ? base : (1 - base) / 2);
        probabilities.put("neutral", "neutral".equals(result.getLabel()) ? base : (1 - base) / 2);
        probabilities.put("negative", "negative".equals(result.getLabel()) ? base : (1 - base) / 2);
        response.put("probabilities", probabilities);
        
        return response;
    }

    @Data
    public static class SentimentResult {
        private String label;
        private float score;
        private float confidence;

        public static SentimentResult neutral() {
            SentimentResult result = new SentimentResult();
            result.setLabel("neutral");
            result.setScore(0.0f);
            result.setConfidence(0.5f);
            return result;
        }
    }
}
