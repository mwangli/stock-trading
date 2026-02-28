package com.stock.models.service;

import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.models.config.LstmTrainingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * LSTM 训练数据预处理服务
 * 负责特征工程、数据归一化、时间序列构建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LstmDataPreprocessor {

    private final PriceRepository priceRepository;
    private final LstmTrainingConfig config;

    /**
     * 获取并预处理单只股票的训练数据
     */
    public TrainingData prepareTrainingData(String stockCode, int days) {
        log.info("准备股票 {} 的训练数据，天数：{}", stockCode, days);

        // 1. 从 MongoDB 获取历史价格数据
        List<StockPrice> prices = fetchStockPrices(stockCode, days);
        if (prices == null || prices.isEmpty()) {
            log.warn("股票 {} 没有足够的价格数据", stockCode);
            return null;
        }

        // 2. 特征提取 - 返回 float[days][features]
        float[][] features = extractFeatures(prices);
        if (features == null) {
            log.warn("股票 {} 特征提取失败", stockCode);
            return null;
        }

        // 3. 构建时间序列样本
        SequenceData sequenceData = buildSequences(features);
        if (sequenceData == null) {
            log.warn("股票 {} 序列构建失败", stockCode);
            return null;
        }

        // 4. 归一化
        NormalizedData normalizedData = normalize(sequenceData);

        log.info("股票 {} 数据准备完成：特征维度={}, 样本数={}", 
                stockCode, config.getInputSize(), normalizedData.getFeatures().size());

        return new TrainingData(normalizedData.getFeatures(), normalizedData.getLabels(), 
                                normalizedData.getScalerParams());
    }

    /**
     * 从 MongoDB 获取股票价格数据
     */
    private List<StockPrice> fetchStockPrices(String stockCode, int days) {
        try {
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(stockCode);
            
            if (prices.size() < config.getSequenceLength() + 10) {
                log.warn("股票 {} 的数据量不足（{} < {}），需要更多历史数据", 
                        stockCode, prices.size(), config.getSequenceLength() + 10);
                return Collections.emptyList();
            }

            // 只取最近 N 天的数据
            int maxSize = Math.min(prices.size(), days);
            return prices.subList(prices.size() - maxSize, prices.size());
            
        } catch (Exception e) {
            log.error("获取股票 {} 价格数据失败", stockCode, e);
            return Collections.emptyList();
        }
    }

    /**
     * 提取特征
     * 特征包括：开盘价、最高价、最低价、收盘价、成交量
     * 
     * @return float[days][features=5]
     */
    private float[][] extractFeatures(List<StockPrice> prices) {
        int numDays = prices.size();
        float[][] features = new float[numDays][config.getInputSize()];

        for (int i = 0; i < numDays; i++) {
            StockPrice price = prices.get(i);
            
            // 特征向量：[开盘，最高，最低，收盘，成交量]
            features[i][0] = safeFloat(price.getTodayOpenPrice());
            features[i][1] = safeFloat(price.getPrice1());  // high
            features[i][2] = safeFloat(price.getPrice2());  // low
            features[i][3] = safeFloat(price.getPrice3());  // close
            features[i][4] = safeFloat(price.getTradingVolume());
        }

        return features;
    }

    /**
     * 构建时间序列样本
     * 使用滑动窗口方式：用过去 sequenceLength 天的数据预测下一天
     * 
     * @param features float[days][features]
     * @return 序列特征和标签
     */
    private SequenceData buildSequences(float[][] features) {
        int sequenceLength = config.getSequenceLength();
        int numSamples = features.length - sequenceLength;

        if (numSamples <= 0) {
            log.warn("数据量不足以构建序列（{} < {}）", features.length, sequenceLength);
            return null;
        }

        List<float[][]> featureSequences = new ArrayList<>();
        List<float[]> labels = new ArrayList<>();

        for (int i = 0; i < numSamples; i++) {
            // 输入：过去 N 天的特征
            float[][] sequence = new float[sequenceLength][config.getInputSize()];
            for (int j = 0; j < sequenceLength; j++) {
                System.arraycopy(features[i + j], 0, sequence[j], 0, config.getInputSize());
            }

            // 标签：下一天的收盘价（特征索引 3）
            float label = features[i + sequenceLength][3];

            featureSequences.add(sequence);
            labels.add(new float[]{label});
        }

        return new SequenceData(featureSequences, labels);
    }

    /**
     * 数据归一化（Min-Max 标准化）
     * 将数据缩放到 [0, 1] 范围
     */
    private NormalizedData normalize(SequenceData data) {
        List<float[][]> normalizedFeatures = new ArrayList<>();
        List<float[]> normalizedLabels = new ArrayList<>();
        float[][] scalerParams = new float[config.getInputSize() + 1][2]; // [min, max]

        // 1. 合并所有特征用于计算全局 min/max
        int sequenceLength = config.getSequenceLength();
        int numSamples = data.featureSequences.size();
        
        float[][] allFeatures = new float[numSamples * sequenceLength][config.getInputSize()];
        float[] allLabels = new float[numSamples];

        int idx = 0;
        for (int s = 0; s < numSamples; s++) {
            float[][] seq = data.featureSequences.get(s);
            for (int t = 0; t < sequenceLength; t++) {
                allFeatures[idx] = seq[t].clone();
                
                for (int f = 0; f < config.getInputSize(); f++) {
                    if (idx == 0) {
                        scalerParams[f][0] = allFeatures[idx][f]; // min
                        scalerParams[f][1] = allFeatures[idx][f]; // max
                    } else {
                        scalerParams[f][0] = Math.min(scalerParams[f][0], allFeatures[idx][f]);
                        scalerParams[f][1] = Math.max(scalerParams[f][1], allFeatures[idx][f]);
                    }
                }
                idx++;
            }
            allLabels[s] = data.labels.get(s)[0];
        }

        // 标签的归一化参数
        int labelIdx = config.getInputSize();
        scalerParams[labelIdx][0] = allLabels[0];
        scalerParams[labelIdx][1] = allLabels[0];
        for (float label : allLabels) {
            scalerParams[labelIdx][0] = Math.min(scalerParams[labelIdx][0], label);
            scalerParams[labelIdx][1] = Math.max(scalerParams[labelIdx][1], label);
        }

        // 2. 归一化特征
        for (float[][] seq : data.featureSequences) {
            float[][] normalized = new float[sequenceLength][config.getInputSize()];
            for (int t = 0; t < sequenceLength; t++) {
                for (int f = 0; f < config.getInputSize(); f++) {
                    float min = scalerParams[f][0];
                    float max = scalerParams[f][1];
                    float range = max - min;
                    normalized[t][f] = (range > 0) ? (seq[t][f] - min) / range : 0.5f;
                }
            }
            normalizedFeatures.add(normalized);
        }

        // 3. 归一化标签
        for (float[] label : data.labels) {
            float min = scalerParams[labelIdx][0];
            float max = scalerParams[labelIdx][1];
            float range = max - min;
            float normalizedVal = (range > 0) ? (label[0] - min) / range : 0.5f;
            normalizedLabels.add(new float[]{normalizedVal});
        }

        log.info("数据归一化完成，特征范围：[{:.2f}, {:.2f}], 标签范围：[{:.2f}, {:.2f}]",
                scalerParams[0][0], scalerParams[0][1],
                scalerParams[labelIdx][0], scalerParams[labelIdx][1]);

        return new NormalizedData(normalizedFeatures, normalizedLabels, scalerParams);
    }

    /**
     * 安全的 float 转换
     */
    private float safeFloat(BigDecimal value) {
        return value != null ? value.floatValue() : 0f;
    }

    // ========== 数据载体类 ==========

    /**
     * 训练数据
     */
    @lombok.Data
    @lombok.RequiredArgsConstructor
    public static class TrainingData {
        private final List<float[][]> features;
        private final List<float[]> labels;
        private final float[][] scalerParams;
    }

    /**
     * 序列数据（归一化前）
     */
    @lombok.Data
    @lombok.RequiredArgsConstructor
    private static class SequenceData {
        private final List<float[][]> featureSequences;
        private final List<float[]> labels;
    }

    /**
     * 归一化后的数据
     */
    @lombok.Data
    @lombok.RequiredArgsConstructor
    private static class NormalizedData {
        private final List<float[][]> features;
        private final List<float[]> labels;
        private final float[][] scalerParams;
    }
}
