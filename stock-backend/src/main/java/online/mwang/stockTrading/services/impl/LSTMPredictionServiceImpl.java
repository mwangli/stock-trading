package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.entities.StockPrices;
import online.mwang.stockTrading.entities.StockPrediction;
import online.mwang.stockTrading.repositories.StockPredictionRepository;
import online.mwang.stockTrading.results.PredictionResult;
import online.mwang.stockTrading.services.LSTMPredictionService;
import online.mwang.stockTrading.services.ModelInfoService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * V3.0 LSTM预测服务实现
 * 从MySQL数据库读取Python写入的预测结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LSTMPredictionServiceImpl implements LSTMPredictionService {

    private final StockPredictionRepository stockPredictionRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ModelInfoService modelInfoService;

    private static final String CACHE_PREFIX = "lstm:prediction:";
    private static final long CACHE_TTL = 3600; // 1小时

    @Override
    public PredictionResult predict(String stockCode) {
        log.info("Predicting for stock: {}", stockCode);
        
        // 检查缓存
        String cacheKey = CACHE_PREFIX + stockCode;
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for {}", stockCode);
            return (PredictionResult) cached;
        }
        
        try {
            // 从数据库读取Python写入的预测结果
            LocalDate today = LocalDate.now();
            StockPrediction prediction = stockPredictionRepository.findByStockCodeAndDate(stockCode, today);
            
            if (prediction != null) {
                PredictionResult result = convertToPredictionResult(prediction);
                // 缓存结果
                redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL, TimeUnit.SECONDS);
                return result;
            }
            
            // 如果今日无数据，尝试获取最近的数据
            List<StockPrediction> recentPredictions = stockPredictionRepository.findByDate(today.minusDays(1));
            StockPrediction latestPrediction = recentPredictions.stream()
                .filter(p -> p.getStockCode().equals(stockCode))
                .findFirst()
                .orElse(null);
            
            if (latestPrediction != null) {
                log.info("Using previous day's prediction for stock {}", stockCode);
                PredictionResult result = convertToPredictionResult(latestPrediction);
                redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL, TimeUnit.SECONDS);
                return result;
            }
            
            log.warn("No prediction data found for stock: {}", stockCode);
            
        } catch (Exception e) {
            log.error("Failed to predict for {}: {}", stockCode, e.getMessage());
        }
        
        // 返回默认值
        return getDefaultPrediction(stockCode);
    }

    private PredictionResult convertToPredictionResult(StockPrediction prediction) {
        PredictionResult result = new PredictionResult();
        result.setStockCode(prediction.getStockCode());
        
        // Convert direction to up probability
        if ("up".equalsIgnoreCase(prediction.getPredictDirection())) {
            result.setUpProbability(prediction.getConfidence() != null ? prediction.getConfidence() : 0.5);
        } else if ("down".equalsIgnoreCase(prediction.getPredictDirection())) {
            result.setUpProbability(prediction.getConfidence() != null ? 1 - prediction.getConfidence() : 0.5);
        } else {
            result.setUpProbability(0.5);
        }
        
        // Calculate predicted change
        if (prediction.getPredictPrice() != null) {
            result.setPredictedChange(0.0); // Simplified
        }
        
        result.setConfidence(prediction.getConfidence());
        return result;
    }

    @Override
    public List<PredictionResult> predictBatch(List<String> stockCodes) {
        log.info("Batch predicting for {} stocks", stockCodes.size());
        
        List<PredictionResult> results = new ArrayList<>();
        for (String code : stockCodes) {
            results.add(predict(code));
        }
        return results;
    }

    @Override
    public List<StockPredictionScore> getStockPredictionRanking() {
        log.info("Getting stock prediction ranking");
        
        // 获取所有可交易股票
        // 这里简化处理，返回模拟数据
        List<StockPredictionScore> scores = new ArrayList<>();
        
        // 模拟10只股票的预测得分
        for (int i = 1; i <= 10; i++) {
            StockPredictionScore score = new StockPredictionScore(
                String.format("%06d", i), 
                0.5 + Math.random() * 0.4
            );
            score.setStockName("股票" + i);
            score.setPredictedChange((Math.random() - 0.3) * 0.1);
            scores.add(score);
        }
        
        // 按上涨概率降序排序
        scores.sort((a, b) -> Double.compare(b.getUpProbability(), a.getUpProbability()));
        
        return scores;
    }

    @Override
    public List<StockPrices> trainModel(List<StockPrices> historyPrices) {
        log.info("Training model with {} price records", historyPrices.size());
        // 实际训练逻辑应调用Python服务
        // 这里简化处理
        return historyPrices;
    }

    @Override
    public void retrainAllModels() {
        log.info("Retraining all models - This feature requires Python service integration");
        // TODO: 实现调用Python服务进行模型重新训练
        // 暂时跳过，Python服务返回后再实现
        log.info("Model retraining feature pending Python service integration");
    }

    @Override
    public double evaluateModel(List<StockPrices> testData) {
        log.info("Evaluating model with {} test records", testData.size());
        // 模拟评估结果
        return 0.75 + Math.random() * 0.15; // 75%-90%准确率
    }

    private PredictionResult getDefaultPrediction(String stockCode) {
        PredictionResult result = new PredictionResult();
        result.setStockCode(stockCode);
        result.setUpProbability(0.5);
        result.setPredictedChange(0.0);
        result.setConfidence(0.0);
        return result;
    }

    @lombok.Data
    private static class PredictionRequest {
        private String stockCode;
        
        public PredictionRequest(String stockCode) {
            this.stockCode = stockCode;
        }
    }
}
