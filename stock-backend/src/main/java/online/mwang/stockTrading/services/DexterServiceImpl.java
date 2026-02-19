package online.mwang.stockTrading.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.models.DexterResult;
import online.mwang.stockTrading.services.SentimentAnalysisService;
import online.mwang.stockTrading.services.LSTMPredictionService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
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
            
            String recommendation = generateRecommendation(combinedScore);
            String answer = buildAnalysisAnswer(combinedScore, sentimentScore, prediction.getUpProbability());
            String detail = buildDetailAnswer(sentimentScore, prediction.getUpProbability(), 
                getTechnicalScore(stockCode), getFundamentalScore(stockCode));
            
            DexterResult result = DexterResult.builder()
                .stockCode(stockCode)
                .analysisType("MULTI_FACTOR")
                .answer(answer)
                .detail(detail)
                .score(combinedScore)
                .label(recommendation)
                .analysisTime(new Date())
                .source("Dexter-AI")
                .build();
            
            // 缓存结果
            redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL, TimeUnit.SECONDS);
            
            return result;
            
        } catch (Exception e) {
            log.error("Dexter analysis failed for {}: {}", stockCode, e.getMessage());
            return DexterResult.builder()
                .stockCode(stockCode)
                .analysisType("MULTI_FACTOR")
                .answer("分析失败，建议持有")
                .detail("分析过程中出现错误: " + e.getMessage())
                .score(0.5)
                .label("HOLD")
                .analysisTime(new Date())
                .source("Dexter-AI-Fallback")
                .build();
        }
    }

    @Override
    public DexterResult getNextDayAdvice(String stockCode) {
        log.info("Getting next day advice for: {}", stockCode);
        DexterResult result = analyzeStock(stockCode);
        
        // 转换为次日建议格式
        return DexterResult.builder()
            .stockCode(stockCode)
            .analysisType("NEXT_DAY_ADVICE")
            .answer(result.getAnswer())
            .detail(result.getDetail())
            .score(result.getScore())
            .label(result.getLabel())
            .analysisTime(new Date())
            .source("Dexter-AI")
            .build();
    }

    @Override
    public List<DexterResult> analyzeBatch(List<String> stockCodes) {
        log.info("Batch analyzing {} stocks", stockCodes.size());
        List<DexterResult> results = new ArrayList<>();
        for (String stockCode : stockCodes) {
            try {
                results.add(analyzeStock(stockCode));
            } catch (Exception e) {
                log.warn("Failed to analyze {}: {}", stockCode, e.getMessage());
            }
        }
        return results;
    }

    @Override
    public double adviceToScore(String advice) {
        if (advice == null || advice.isEmpty()) {
            return 0.5;
        }
        
        String lowerAdvice = advice.toLowerCase();
        if (lowerAdvice.contains("强烈买入") || lowerAdvice.contains("strong buy")) {
            return 0.9;
        } else if (lowerAdvice.contains("买入") || lowerAdvice.contains("buy")) {
            return 0.7;
        } else if (lowerAdvice.contains("持有") || lowerAdvice.contains("hold")) {
            return 0.5;
        } else if (lowerAdvice.contains("卖出") || lowerAdvice.contains("sell")) {
            return 0.3;
        } else if (lowerAdvice.contains("强烈卖出") || lowerAdvice.contains("strong sell")) {
            return 0.1;
        }
        
        return 0.5;
    }

    @Override
    public List<StockDexterScore> getStockDexterRanking() {
        log.info("Getting stock Dexter ranking");
        List<StockDexterScore> ranking = new ArrayList<>();
        
        // 从缓存获取已分析的股票
        // 简化实现：返回空列表，实际应从数据库或缓存获取
        return ranking;
    }

    @Override
    public String getStockDetail(String stockCode) {
        log.info("Getting stock detail for: {}", stockCode);
        DexterResult result = analyzeStock(stockCode);
        return result.getDetail();
    }

    private Map<String, Double> getFactorWeights() {
        Map<String, Double> weights = new HashMap<>();
        weights.put("sentiment", 0.25);
        weights.put("prediction", 0.35);
        weights.put("technical", 0.25);
        weights.put("fundamental", 0.15);
        return weights;
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

    private String buildAnalysisAnswer(double combinedScore, double sentimentScore, double predictionScore) {
        String trend = combinedScore >= 0.5 ? "看涨" : "看跌";
        return String.format("综合评分%.2f，%s趋势。情感得分%.2f，预测上涨概率%.2f", 
            combinedScore, trend, sentimentScore, predictionScore);
    }

    private String buildDetailAnswer(double sentiment, double prediction, 
                                     double technical, double fundamental) {
        return String.format(
            "详细分析：情感分析得分=%.2f，LSTM预测得分=%.2f，技术指标得分=%.2f，基本面得分=%.2f",
            sentiment, prediction, technical, fundamental
        );
    }

    private double normalize(double score) {
        // 将-1到1的得分归一化到0到1
        return (score + 1) / 2;
    }
}
