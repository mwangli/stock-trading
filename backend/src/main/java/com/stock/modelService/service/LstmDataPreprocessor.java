package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.modelService.config.LstmTrainingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * LSTM 数据预处理器
 * 将股票价格数据转换为LSTM训练所需的格式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LstmDataPreprocessor {

    private final LstmTrainingConfig config;
private final TechnicalIndicatorService technicalIndicatorService;

    /**
     * 处理训练数据
     * 
     * @param prices 股票价格列表
     * @return 处理后的训练数据
     */
    public ProcessedData processData(List<StockPrice> prices) {
        if (prices == null || prices.size() < config.getSequenceLength() + 1) {
            log.warn("数据不足: 需要至少 {} 条记录，实际 {}", config.getSequenceLength() + 1, 
                    prices == null ? 0 : prices.size());
            return null;
        }

        log.info("开始处理 {} 条价格数据", prices.size());

        // 1. 数据归一化参数
        double maxPrice = prices.stream()
                .mapToDouble(p -> p.getHighPrice() != null ? p.getHighPrice().doubleValue() : 0)
                .max()
                .orElse(1.0);
        
        double maxVolume = prices.stream()
                .mapToDouble(p -> p.getVolume() != null ? p.getVolume().doubleValue() : 0)
                .max()
                .orElse(1.0);

        log.info("归一化参数 - 最高价: {}, 最大成交量: {}", maxPrice, maxVolume);

        log.info("开始计算技术指标...");
        Map<String, double[]> indicators = technicalIndicatorService.calculateIndicators(prices);
        double[] rsi = indicators.get("RSI");
        double[] macd = indicators.get("MACD");
        double[] sma = indicators.get("SMA");
        double[] upperBoll = indicators.get("UpperBoll");
        double[] lowerBoll = indicators.get("LowerBoll");
        double[] obv = indicators.get("OBV");

        // 为指标找到归一化参数 (使用最大绝对值)
        double maxRsi = Arrays.stream(rsi).max().orElse(1.0);
        double minRsi = Arrays.stream(rsi).min().orElse(0.0);
        double maxMacd = Arrays.stream(macd).map(Math::abs).max().orElse(1.0);
        double maxSma = Arrays.stream(sma).max().orElse(1.0);
        double maxUpperBoll = Arrays.stream(upperBoll).max().orElse(1.0);
        double maxLowerBoll = Arrays.stream(lowerBoll).max().orElse(1.0);
        double maxObv = Arrays.stream(obv).map(Math::abs).max().orElse(1.0);

        log.info("技术指标计算完成.");


        // 2. 转换为特征矩阵
        List<double[]> features = new ArrayList<>();
        List<Double> targets = new ArrayList<>();

        for (int i = 0; i < prices.size(); i++) {
            StockPrice price = prices.get(i);
            double[] feature = new double[config.getInputSize()];
            
            // 特征: [开盘价/最高价, 最高价/最高价, 最低价/最高价, 收盘价/最高价, 成交量/最大成交量]
            feature[0] = normalize(price.getOpenPrice(), maxPrice);
            feature[1] = normalize(price.getHighPrice(), maxPrice);
            feature[2] = normalize(price.getLowPrice(), maxPrice);
            feature[3] = normalize(price.getClosePrice(), maxPrice);
            feature[4] = normalizeVolume(price.getVolume(), maxVolume);
            feature[5] = (rsi[i] - minRsi) / (maxRsi - minRsi); // Min-Max scaling for RSI
            feature[6] = macd[i] / maxMacd; // Normalize MACD
            feature[7] = sma[i] / maxSma; // Normalize SMA
            feature[8] = upperBoll[i] / maxUpperBoll;
            feature[9] = lowerBoll[i] / maxLowerBoll;
            feature[10] = obv[i] / maxObv;
            
            features.add(feature);
            
            // 目标: 下一天的收盘价
            if (i < prices.size() - 1) {
                targets.add(prices.get(i + 1).getClosePrice().doubleValue() / maxPrice);
            }
        }

        // 3. 构建训练样本
        List<TrainingSample> samples = createSamples(features, targets);

        // 4. 划分训练集和验证集
        int trainSize = (int) (samples.size() * config.getTrainRatio());
        // 确保至少有一个训练样本（当样本总数 > 0 时）
        if (trainSize == 0 && samples.size() > 0) {
            trainSize = 1;
        }
        // 防止 trainSize 等于 samples.size() 导致验证集为空（这是允许的）
        List<TrainingSample> trainSamples = samples.subList(0, Math.min(trainSize, samples.size()));
        List<TrainingSample> valSamples = samples.subList(Math.min(trainSize, samples.size()), samples.size());

        log.info("数据预处理完成 - 总样本: {}, 训练集: {}, 验证集: {}", 
                samples.size(), trainSamples.size(), valSamples.size());

        return ProcessedData.builder()
                .trainSamples(trainSamples)
                .valSamples(valSamples)
                .maxPrice(maxPrice)
                .maxVolume(maxVolume)
                .maxRsi(maxRsi)
                .minRsi(minRsi)
                .maxMacd(maxMacd)
                .maxSma(maxSma)
                .maxUpperBoll(maxUpperBoll)
                .maxLowerBoll(maxLowerBoll)
                .maxObv(maxObv)
                .featureCount(config.getInputSize())
                .sequenceLength(config.getSequenceLength())
                .build();
    }

    /**
     * 构建预测所需的最新序列输入
     *
     * 使用与训练阶段完全一致的归一化和技术指标计算方式，
     * 从价格列表末尾截取 sequenceLength 条记录，生成单条预测输入样本。
     *
     * @param prices 股票价格列表（按日期升序排列）
     * @return 预测输入包装对象，包含归一化后的特征序列、最大价格和最新收盘价
     */
    public PredictionInput buildPredictionInput(List<StockPrice> prices) {
        if (prices == null || prices.size() < config.getSequenceLength()) {
            log.warn("构建预测输入数据不足: 需要至少 {} 条记录，实际 {}",
                    config.getSequenceLength(), prices == null ? 0 : prices.size());
            return null;
        }

        // 1. 计算价格与成交量归一化参数（与 processData 保持一致）
        double maxPrice = prices.stream()
                .mapToDouble(p -> p.getHighPrice() != null ? p.getHighPrice().doubleValue() : 0)
                .max()
                .orElse(1.0);

        double maxVolume = prices.stream()
                .mapToDouble(p -> p.getVolume() != null ? p.getVolume().doubleValue() : 0)
                .max()
                .orElse(1.0);

        log.info("构建预测输入 - 归一化参数: maxPrice={}, maxVolume={}", maxPrice, maxVolume);

        // 2. 计算技术指标并获取归一化参数（与 processData 保持一致）
        Map<String, double[]> indicators = technicalIndicatorService.calculateIndicators(prices);
        double[] rsi = indicators.get("RSI");
        double[] macd = indicators.get("MACD");
        double[] sma = indicators.get("SMA");
        double[] upperBoll = indicators.get("UpperBoll");
        double[] lowerBoll = indicators.get("LowerBoll");
        double[] obv = indicators.get("OBV");

        double maxRsi = Arrays.stream(rsi).max().orElse(1.0);
        double minRsi = Arrays.stream(rsi).min().orElse(0.0);
        double maxMacd = Arrays.stream(macd).map(Math::abs).max().orElse(1.0);
        double maxSma = Arrays.stream(sma).max().orElse(1.0);
        double maxUpperBoll = Arrays.stream(upperBoll).max().orElse(1.0);
        double maxLowerBoll = Arrays.stream(lowerBoll).max().orElse(1.0);
        double maxObv = Arrays.stream(obv).map(Math::abs).max().orElse(1.0);

        int seqLen = config.getSequenceLength();
        int featureSize = config.getInputSize();
        float[][] input = new float[seqLen][featureSize];

        // 3. 从价格序列末尾截取 sequenceLength 条记录，构建特征矩阵
        int startIndex = prices.size() - seqLen;
        for (int j = 0; j < seqLen; j++) {
            int idx = startIndex + j;
            StockPrice price = prices.get(idx);
            double[] feature = new double[featureSize];

            feature[0] = normalize(price.getOpenPrice(), maxPrice);
            feature[1] = normalize(price.getHighPrice(), maxPrice);
            feature[2] = normalize(price.getLowPrice(), maxPrice);
            feature[3] = normalize(price.getClosePrice(), maxPrice);
            feature[4] = normalizeVolume(price.getVolume(), maxVolume);
            feature[5] = (rsi[idx] - minRsi) / (maxRsi - minRsi);
            feature[6] = macd[idx] / maxMacd;
            feature[7] = sma[idx] / maxSma;
            feature[8] = upperBoll[idx] / maxUpperBoll;
            feature[9] = lowerBoll[idx] / maxLowerBoll;
            feature[10] = obv[idx] / maxObv;

            for (int k = 0; k < feature.length && k < featureSize; k++) {
                input[j][k] = (float) feature[k];
            }
        }

        double lastClosePrice = prices.get(prices.size() - 1).getClosePrice() != null
                ? prices.get(prices.size() - 1).getClosePrice().doubleValue()
                : 0.0;

        return PredictionInput.builder()
                .input(input)
                .maxPrice(maxPrice)
                .lastClosePrice(lastClosePrice)
                .build();
    }

    /**
     * 创建训练样本
     * 使用滑动窗口方式构建序列
     */
    private List<TrainingSample> createSamples(List<double[]> features, List<Double> targets) {
        List<TrainingSample> samples = new ArrayList<>();
        int seqLen = config.getSequenceLength();

        for (int i = 0; i <= features.size() - seqLen - 1; i++) {
            float[][] input = new float[seqLen][config.getInputSize()];
            
            for (int j = 0; j < seqLen; j++) {
                double[] feature = features.get(i + j);
                for (int k = 0; k < feature.length && k < config.getInputSize(); k++) {
                    input[j][k] = (float) feature[k];
                }
            }
            
            float target = targets.get(i + seqLen - 1).floatValue();
            samples.add(new TrainingSample(input, target));
        }

        return samples;
    }

    /**
     * 归一化价格
     */
    private double normalize(java.math.BigDecimal value, double max) {
        if (value == null || max == 0) return 0;
        return value.doubleValue() / max;
    }

    /**
     * 归一化成交量
     */
    private double normalizeVolume(java.math.BigDecimal volume, double max) {
        if (volume == null || max == 0) return 0;
        return volume.doubleValue() / max;
    }

    /**
     * 训练样本
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class TrainingSample {
        private float[][] input;  // [sequenceLength, inputSize]
        private float target;     // 预测目标
    }

    /**
     * 处理后的数据
     */
    @lombok.Data
    @lombok.Builder
    public static class ProcessedData {
        private List<TrainingSample> trainSamples;
        private List<TrainingSample> valSamples;
        private double maxPrice;
        private double maxVolume;
        private double maxRsi;
        private double minRsi;
        private double maxMacd;
        private double maxSma;
        private double maxUpperBoll;
        private double maxLowerBoll;
        private double maxObv;
        private int featureCount;
        private int sequenceLength;

        /**
         * 获取训练输入数据
         */
        public float[][][] getTrainInputs() {
            float[][][] inputs = new float[trainSamples.size()][][];
            for (int i = 0; i < trainSamples.size(); i++) {
                inputs[i] = trainSamples.get(i).getInput();
            }
            return inputs;
        }

        /**
         * 获取训练目标数据
         */
        public float[] getTrainTargets() {
            float[] targets = new float[trainSamples.size()];
            for (int i = 0; i < trainSamples.size(); i++) {
                targets[i] = trainSamples.get(i).getTarget();
            }
            return targets;
        }

        /**
         * 获取验证输入数据
         */
        public float[][][] getValInputs() {
            float[][][] inputs = new float[valSamples.size()][][];
            for (int i = 0; i < valSamples.size(); i++) {
                inputs[i] = valSamples.get(i).getInput();
            }
            return inputs;
        }

        /**
         * 获取验证目标数据
         */
        public float[] getValTargets() {
            float[] targets = new float[valSamples.size()];
            for (int i = 0; i < valSamples.size(); i++) {
                targets[i] = valSamples.get(i).getTarget();
            }
            return targets;
        }
    }

    /**
     * 单条预测输入包装类
     * 用于封装 LSTM 预测所需的序列特征和反归一化参数
     */
    @lombok.Data
    @lombok.Builder
    public static class PredictionInput {
        /**
         * 预测输入特征，形状为 [sequenceLength, inputSize]
         */
        private float[][] input;

        /**
         * 价格归一化时使用的最大价格（用于反归一化预测结果）
         */
        private double maxPrice;

        /**
         * 最新一个交易日的收盘价（原始价格）
         */
        private double lastClosePrice;
    }
}
