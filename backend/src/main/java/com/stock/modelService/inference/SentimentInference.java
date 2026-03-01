package com.stock.modelService.inference;

import com.stock.modelService.dto.SentimentAnalysisResult;
import com.stock.modelService.service.SentimentTrainerService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 情感分析推理组件
 * 集成真实的 BERT 模型推理功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentInference {

    private final SentimentTrainerService trainerService;

    private boolean isLoaded = false;
    private LocalDateTime lastLoadedTime;

    /**
     * 初始化时加载模型
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        loadModel();
    }

    /**
     * 加载情感分析模型
     */
    public void loadModel() {
        try {
            log.info("情感分析模型加载中...");
            boolean success = trainerService.loadModel();
            this.isLoaded = success;
            if (success) {
                this.lastLoadedTime = LocalDateTime.now();
                log.info("情感分析模型加载成功");
            } else {
                log.warn("情感分析模型加载失败，使用规则推理模式");
            }
        } catch (Exception e) {
            log.error("加载情感分析模型失败", e);
            isLoaded = false;
        }
    }

    /**
     * 分析文本情感
     */
    public SentimentResult analyze(String text) {
        if (text == null || text.trim().isEmpty()) {
            return SentimentResult.neutral();
        }

        try {
            SentimentAnalysisResult result = trainerService.analyzeSentiment(text);
            
            SentimentResult sentimentResult = new SentimentResult();
            sentimentResult.setLabel(result.getLabel());
            sentimentResult.setScore((float) result.getScore());
            sentimentResult.setConfidence((float) result.getConfidence());

            return sentimentResult;

        } catch (Exception e) {
            log.error("情感分析失败", e);
            return SentimentResult.neutral();
        }
    }

    /**
     * 详细的情感分析结果
     */
    public Map<String, Object> analyzeWithDetails(String text) {
        try {
            SentimentAnalysisResult result = trainerService.analyzeSentiment(text);

            Map<String, Object> response = new HashMap<>();
            response.put("label", result.getLabel());
            response.put("score", result.getScore());
            response.put("confidence", result.getConfidence());
            response.put("probabilities", result.getProbabilities());
            response.put("text", result.getText());
            response.put("modelLoaded", isLoaded);

            return response;

        } catch (Exception e) {
            log.error("情感分析失败", e);
            return analyzeWithDetailsFallback(text);
        }
    }

    /**
     * 降级方案：基于规则的分析
     */
    private Map<String, Object> analyzeWithDetailsFallback(String text) {
        SentimentResult result = analyzeSimple(text);

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
        response.put("modelLoaded", false);

        return response;
    }

    /**
     * 简单的关键词匹配（备用）
     */
    private SentimentResult analyzeSimple(String text) {
        String lowerText = text.toLowerCase();
        int positiveCount = 0;
        int negativeCount = 0;

        String[] positiveWords = {"增长", "盈利", "上涨", "突破", "利好", "推荐", "买入", "收益", "业绩"};
        String[] negativeWords = {"下跌", "亏损", "风险", "减持", "利空", "卖出", "警告", "业绩下滑"};

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

    /**
     * 重新加载模型
     */
    public void reloadModel() {
        log.info("重新加载情感分析模型...");
        trainerService.unloadModel();
        loadModel();
    }

    public boolean isLoaded() {
        return isLoaded;
    }

    public LocalDateTime getLastLoadedTime() {
        return lastLoadedTime;
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
