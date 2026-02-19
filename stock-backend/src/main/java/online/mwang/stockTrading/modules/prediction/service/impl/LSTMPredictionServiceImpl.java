package online.mwang.stockTrading.modules.prediction.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.prediction.LSTMPredictionService;
import online.mwang.stockTrading.modules.prediction.model.PredictionResult;
import online.mwang.stockTrading.modules.prediction.service.ModelInfoService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LSTM预测服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LSTMPredictionServiceImpl implements LSTMPredictionService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ModelInfoService modelInfoService;

    private static final String AI_SERVICE_URL = "http://localhost:8001";
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
            // 调用Python AI服务
            String url = AI_SERVICE_URL + "/api/lstm/predict";
            PredictionRequest request = new PredictionRequest(stockCode);
            PredictionResult result = restTemplate.postForObject(url, request, PredictionResult.class);
            
            if (result != null) {
                // 缓存结果
                redisTemplate.opsForValue().set(cacheKey, result, CACHE_TTL, TimeUnit.SECONDS);
                return result;
            }
        } catch (Exception e) {
            log.error("Failed to predict for {}: {}", stockCode, e.getMessage());
        }
        
        // 返回默认值
        return getDefaultPrediction(stockCode);
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
        log.info("Retraining all models");
        try {
            String url = AI_SERVICE_URL + "/api/lstm/retrain";
            restTemplate.postForObject(url, null, String.class);
            log.info("Model retraining initiated");
        } catch (Exception e) {
            log.error("Failed to retrain models: {}", e.getMessage());
        }
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
