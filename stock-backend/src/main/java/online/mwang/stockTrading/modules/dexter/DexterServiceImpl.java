package online.mwang.stockTrading.modules.dexter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.dexter.model.DexterResult;
import online.mwang.stockTrading.modules.sentiment.service.SentimentAnalysisService;
import online.mwang.stockTrading.modules.prediction.LSTMPredictionService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Dexter多因子分析服务实现
 * 融合情感分析、LSTM预测、技术指标的多模态AI
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DexterServiceImpl implements DexterService {

    private final SentimentAnalysisService sentimentService;
    private final LSTMPredictionService predictionService;
    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String AI_SERVICE_URL = "http://localhost:8001";
    private static final String CACHE_PREFIX = "dexter:analysis:";
    private static final long CACHE_TTL = 3600;

    @Override
    public DexterResult analyzeStock(String stockCode) {
        log.info("Dexter analyzing stock: {}", stockCode);
        
        // 检查缓存
        String cacheKey = CACHE_PREFIX + stockCode;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return (DexterResult) cached;
        }
        
        DexterResult result = new DexterResult();
        result.setStockCode(stockCode);
        result.setAnalyzedAt(LocalDateTime.now());
        
        try {
            // 1. 获取各维度评分
            double sentimentScore = sentimentService.analyze("分析 " + stockCode + " 的新闻情感");
            var prediction = predictionService.predict(stockCode);
            
            // 2. 计算多因子融合得分
            double combinedScore = calculateCombinedScore(
                sentimentScore, 
                prediction.getUpProbability(),
                getTechnicalScore(stockCode),
                getFundamentalScore(stockCode)
            );
            
            result.setSentimentScore(normalize(sentimentScore));
            result.setPredictionScore(prediction.getUpProbability());
            result.setTechnicalScore(getTechnicalScore(stockCode));
            result.setFundamentalScore(getFundamentalScore(stockCode));
            result.setCombinedScore(combinedScore);
            result.setRecommendation(generateRecommendation(combinedScore));
            result.setConfidence(calculateConfidence(result));
            
            // 缓存结果
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL, TimeUnit.SECONDS);
            
        } catch (Exception e) {
            log.error("Dexter analysis failed for {}: {}", stockCode, e.getMessage());
            result.setCombinedScore(0.5);
            result.setRecommendation("HOLD");
            result.setConfidence(0.0);
        }
        
        return result;
    }

    @Override
    public Map<String, Double> getFactorWeights() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("sentiment", 0.25);
        weights.put("prediction", 0.35);
        weights.put("technical", 0.25);
        weights.put("fundamental", 0.15);
        return weights;
    }

    @Override
    public boolean isMultiModalReady() {
        // 检查所有AI服务是否就绪
        try {
            restTemplate.getForObject(AI_SERVICE_URL + "/health", String.class);
            return true;
        } catch (Exception e) {
            log.warn("AI service not ready: {}", e.getMessage());
            return false;
        }
    }

    private double calculateCombinedScore(double sentiment, double prediction, 
                                         double technical, double fundamental) {
        Map<String, Double> weights = getFactorWeights();
        return sentiment * weights.get("sentiment") +
               prediction * weights.get("prediction") +
               technical * weights.get("technical") +
               fundamental * weights.get("fundamental");
    }

    private double getTechnicalScore(String stockCode) {
        // 简化：随机生成技术指标得分
        return 0.3 + Math.random() * 0.5;
    }

    private double getFundamentalScore(String stockCode) {
        // 简化：随机生成基本面得分
        return 0.4 + Math.random() * 0.4;
    }

    private String generateRecommendation(double score) {
        if (score >= 0.75) return "STRONG_BUY";
        if (score >= 0.6) return "BUY";
        if (score >= 0.4) return "HOLD";
        if (score >= 0.25) return "SELL";
        return "STRONG_SELL";
    }

    private double calculateConfidence(DexterResult result) {
        // 基于各因子的一致性计算置信度
        double avg = (result.getSentimentScore() + result.getPredictionScore() + 
                     result.getTechnicalScore() + result.getFundamentalScore()) / 4;
        double variance = (
            Math.pow(result.getSentimentScore() - avg, 2) +
            Math.pow(result.getPredictionScore() - avg, 2) +
            Math.pow(result.getTechnicalScore() - avg, 2) +
            Math.pow(result.getFundamentalScore() - avg, 2)
        ) / 4;
        
        // 方差越小，置信度越高
        return Math.max(0, 1 - variance * 4);
    }

    private double normalize(double score) {
        // 将-1到1的得分归一化到0到1
        return (score + 1) / 2;
    }
}
