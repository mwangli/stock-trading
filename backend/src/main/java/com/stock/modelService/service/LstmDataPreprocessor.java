// AI_GENERATE_START --------
package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.modelService.config.LstmModelConfig;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LSTM 面板数据预处理器。
 * 使用收益率、滚动量价指标以及股票和行业静态特征构建共享模型输入，
 * 所有特征只依赖当前及以前交易日，训练与推理执行同一套计算逻辑。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LstmDataPreprocessor {

    /** 当前共享模型特征定义版本。 */
    public static final String FEATURE_VERSION = "panel-embedding-v3";

    /** 当前特征数量。 */
    public static final int FEATURE_COUNT = 16;

    private static final int VOLUME_LOOKBACK = 20;
    private static final double MIN_VALUE = 1.0E-8D;

    private final LstmModelConfig config;
    private final TechnicalIndicatorService technicalIndicatorService;

    /**
     * 处理单只股票训练数据。
     *
     * @param prices 股票价格列表，必须按日期升序排列
     * @return 训练集和验证集
     */
    public ProcessedData processData(List<StockPrice> prices) {
        String stockCode = prices == null || prices.isEmpty() ? "unknown" : prices.get(0).getCode();
        Map<String, List<StockPrice>> priceSeries = Map.of(stockCode, prices == null ? List.of() : prices);
        return processPanelData(priceSeries, Map.of(stockCode, StockContext.basic(stockCode)));
    }

    /**
     * 按股票分别构造滑动窗口后合并为全市场面板数据。
     * 不允许把不同股票的相邻记录拼接成同一个时间窗口。
     *
     * @param priceSeries 股票代码到历史价格序列的映射
     * @param contexts 股票代码到静态特征的映射
     * @return 合并后的训练集和验证集
     */
    public ProcessedData processPanelData(Map<String, List<StockPrice>> priceSeries,
                                          Map<String, StockContext> contexts) {
        validateFeatureCount();
        List<TrainingSample> trainSamples = new ArrayList<>();
        List<TrainingSample> valSamples = new ArrayList<>();
        Map<String, Integer> sampleCounts = new LinkedHashMap<>();

        if (priceSeries == null || priceSeries.isEmpty()) {
            return null;
        }

        int stockCount = Math.max(1, priceSeries.size());
        int panelLimitPerStock = Math.max(4, config.getMaxPanelSamples() / stockCount);
        int sampleLimitPerStock = Math.max(4,
                Math.min(config.getMaxSamplesPerStock(), panelLimitPerStock));

        for (Map.Entry<String, List<StockPrice>> entry : priceSeries.entrySet()) {
            String stockCode = entry.getKey();
            List<StockPrice> prices = entry.getValue();
            StockContext context = contexts == null
                    ? StockContext.basic(stockCode)
                    : contexts.getOrDefault(stockCode, StockContext.basic(stockCode));
            SeriesSamples seriesSamples = createSeriesSamples(prices, context, sampleLimitPerStock);
            if (seriesSamples == null || seriesSamples.allSamples().isEmpty()) {
                continue;
            }

            int total = seriesSamples.allSamples().size();
            int trainEnd = Math.max(1, Math.min(total, (int) Math.floor(total * config.getTrainRatio())));
            int validationStart = Math.min(total, trainEnd + Math.max(0, config.getValidationGap()));
            if (validationStart >= total && trainEnd < total) {
                validationStart = trainEnd;
            }

            trainSamples.addAll(seriesSamples.allSamples().subList(0, trainEnd));
            if (validationStart < total) {
                valSamples.addAll(seriesSamples.allSamples().subList(validationStart, total));
            }
            sampleCounts.put(stockCode, total);
        }

        if (trainSamples.isEmpty()) {
            return null;
        }

        log.info("LSTM 面板数据完成: stocks={}, sampleLimitPerStock={}, trainSamples={}, valSamples={}, featureVersion={}",
                sampleCounts.size(), sampleLimitPerStock, trainSamples.size(), valSamples.size(), FEATURE_VERSION);
        return ProcessedData.builder()
                .trainSamples(trainSamples)
                .valSamples(valSamples)
                .sampleCounts(sampleCounts)
                .featureCount(FEATURE_COUNT)
                .sequenceLength(config.getSequenceLength())
                .targetReturnScale(config.getTargetReturnScale())
                .featureVersion(FEATURE_VERSION)
                .build();
    }

    /**
     * 使用与训练阶段相同的特征口径构造最新预测窗口。
     *
     * @param prices 股票价格列表，必须按日期升序排列
     * @param context 股票静态特征
     * @return 最新预测输入
     */
    public PredictionInput buildPredictionInput(List<StockPrice> prices, StockContext context) {
        validateFeatureCount();
        int sequenceLength = config.getSequenceLength();
        if (prices == null || prices.size() < sequenceLength + 1) {
            log.warn("构建 LSTM 预测输入数据不足: required={}, actual={}",
                    sequenceLength + 1, prices == null ? 0 : prices.size());
            return null;
        }

        StockContext safeContext = context == null
                ? StockContext.basic(prices.get(0).getCode())
                : context;
        List<double[]> features = buildFeatures(prices, safeContext);
        int start = features.size() - sequenceLength;
        float[][] input = new float[sequenceLength][FEATURE_COUNT];
        for (int timeStep = 0; timeStep < sequenceLength; timeStep++) {
            double[] feature = features.get(start + timeStep);
            for (int featureIndex = 0; featureIndex < FEATURE_COUNT; featureIndex++) {
                input[timeStep][featureIndex] = (float) feature[featureIndex];
            }
        }

        return PredictionInput.builder()
                .input(input)
                .lastClosePrice(safeNumber(prices.get(prices.size() - 1).getClosePrice()))
                .featureVersion(FEATURE_VERSION)
                .build();
    }

    private SeriesSamples createSeriesSamples(List<StockPrice> prices, StockContext context,
                                              int sampleLimit) {
        int sequenceLength = config.getSequenceLength();
        if (prices == null || prices.size() < sequenceLength + 2) {
            log.warn("跳过 LSTM 样本不足股票: stockCode={}, required={}, actual={}",
                    context.getStockCode(), sequenceLength + 2, prices == null ? 0 : prices.size());
            return null;
        }

        List<double[]> features = buildFeatures(prices, context);
        List<TrainingSample> samples = new ArrayList<>();
        int possibleSampleCount = prices.size() - sequenceLength;
        int retainedSampleCount = Math.min(possibleSampleCount, Math.max(1, sampleLimit));
        for (int sampleIndex = 0; sampleIndex < retainedSampleCount; sampleIndex++) {
            int start = retainedSampleCount == 1
                    ? possibleSampleCount - 1
                    : (int) Math.round(sampleIndex * (possibleSampleCount - 1D) / (retainedSampleCount - 1D));
            int targetIndex = start + sequenceLength;
            double previousClose = safeNumber(prices.get(targetIndex - 1).getClosePrice());
            double targetClose = safeNumber(prices.get(targetIndex).getClosePrice());
            if (previousClose <= 0D || targetClose <= 0D) {
                continue;
            }

            float[][] input = new float[sequenceLength][FEATURE_COUNT];
            for (int timeStep = 0; timeStep < sequenceLength; timeStep++) {
                double[] feature = features.get(start + timeStep);
                for (int featureIndex = 0; featureIndex < FEATURE_COUNT; featureIndex++) {
                    input[timeStep][featureIndex] = (float) feature[featureIndex];
                }
            }

            double nextReturn = targetClose / previousClose - 1D;
            double scaledTarget = clamp(nextReturn / config.getTargetReturnScale(), -1D, 1D);
            float directionTarget = nextReturn > 0D ? 1F : 0F;
            float downsideTarget = (float) clamp(-nextReturn / config.getTargetReturnScale(), 0D, 1D);
            samples.add(TrainingSample.builder()
                    .input(input)
                    .returnTarget((float) scaledTarget)
                    .directionTarget(directionTarget)
                    .downsideTarget(downsideTarget)
                    .stockCode(context.getStockCode())
                    .targetDate(prices.get(targetIndex).getDate())
                    .build());
        }
        return new SeriesSamples(samples);
    }

    private List<double[]> buildFeatures(List<StockPrice> prices, StockContext context) {
        Map<String, double[]> indicators = technicalIndicatorService.calculateIndicators(prices);
        double[] rsi = indicators.get("RSI");
        double[] macd = indicators.get("MACD");
        double[] sma = indicators.get("SMA");
        double[] upperBoll = indicators.get("UpperBoll");
        double[] lowerBoll = indicators.get("LowerBoll");
        double[] obv = indicators.get("OBV");

        List<double[]> features = new ArrayList<>(prices.size());
        for (int index = 0; index < prices.size(); index++) {
            StockPrice current = prices.get(index);
            double close = safeNumber(current.getClosePrice());
            double previousClose = index == 0
                    ? close
                    : safeNumber(prices.get(index - 1).getClosePrice());
            double rollingVolumeMean = rollingVolumeMean(prices, index, VOLUME_LOOKBACK);
            double rollingVolumeSum = rollingVolumeSum(prices, index, VOLUME_LOOKBACK);
            double currentVolume = Math.max(0D, safeNumber(current.getVolume()));

            double[] feature = new double[FEATURE_COUNT];
            feature[0] = relativeChange(safeNumber(current.getOpenPrice()), previousClose);
            feature[1] = relativeChange(safeNumber(current.getHighPrice()), previousClose);
            feature[2] = relativeChange(safeNumber(current.getLowPrice()), previousClose);
            feature[3] = relativeChange(close, previousClose);
            feature[4] = clamp(Math.log((currentVolume + 1D) / (rollingVolumeMean + 1D)) / 5D, -1D, 1D);
            feature[5] = clamp(safeArrayValue(rsi, index) / 100D, 0D, 1D);
            feature[6] = safeDivide(safeArrayValue(macd, index), close);
            feature[7] = relativeChange(safeArrayValue(sma, index), close);
            feature[8] = relativeChange(safeArrayValue(upperBoll, index), close);
            feature[9] = relativeChange(safeArrayValue(lowerBoll, index), close);
            feature[10] = clamp(safeDivide(safeArrayValue(obv, index), rollingVolumeSum), -1D, 1D);
            int stockIndex = categoricalIndex(context.getStockCode(), config.getStockEmbeddingBuckets());
            int industryIndex = categoricalIndex(context.getIndustryCode() == null
                    ? null : context.getIndustryCode().toString(), config.getIndustryEmbeddingBuckets());
            feature[11] = stockIndex;
            feature[12] = industryIndex;
            feature[13] = groupIndex(industryIndex);
            feature[14] = stableIdentity(context.getMarket());
            double rollingAmountMean = rollingAmountMean(prices, index, VOLUME_LOOKBACK);
            double currentAmount = Math.max(0D, safeNumber(current.getAmount()));
            feature[15] = clamp(Math.log((currentAmount + 1D) / (rollingAmountMean + 1D)) / 5D, -1D, 1D);
            features.add(feature);
        }
        return features;
    }

    private double rollingVolumeMean(List<StockPrice> prices, int endIndex, int lookback) {
        int start = Math.max(0, endIndex - lookback + 1);
        return rollingVolumeSum(prices, endIndex, lookback) / Math.max(1, endIndex - start + 1);
    }

    private double rollingVolumeSum(List<StockPrice> prices, int endIndex, int lookback) {
        int start = Math.max(0, endIndex - lookback + 1);
        double total = 0D;
        for (int index = start; index <= endIndex; index++) {
            total += Math.max(0D, safeNumber(prices.get(index).getVolume()));
        }
        return total;
    }

    private double rollingAmountMean(List<StockPrice> prices, int endIndex, int lookback) {
        int start = Math.max(0, endIndex - lookback + 1);
        double total = 0D;
        for (int index = start; index <= endIndex; index++) {
            total += Math.max(0D, safeNumber(prices.get(index).getAmount()));
        }
        return total / Math.max(1, endIndex - start + 1);
    }

    private double relativeChange(double value, double reference) {
        return reference > MIN_VALUE ? clamp(value / reference - 1D, -1D, 1D) : 0D;
    }

    private double safeDivide(double value, double divisor) {
        return Math.abs(divisor) > MIN_VALUE ? value / divisor : 0D;
    }

    private int categoricalIndex(String value, int bucketCount) {
        if (value == null || value.isBlank() || bucketCount <= 1) {
            return 0;
        }
        return 1 + Math.floorMod(value.hashCode(), bucketCount - 1);
    }

    private int groupIndex(int industryIndex) {
        if (industryIndex <= 0 || config.getGroupHeadCount() <= 1) {
            return 0;
        }
        return 1 + Math.floorMod(industryIndex - 1, config.getGroupHeadCount() - 1);
    }

    private double stableIdentity(String value) {
        if (value == null || value.isBlank()) {
            return 0D;
        }
        return (value.hashCode() & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }

    private double safeArrayValue(double[] values, int index) {
        if (values == null || index < 0 || index >= values.length) {
            return 0D;
        }
        return sanitize(values[index]);
    }

    private double safeNumber(BigDecimal value) {
        return value == null ? 0D : sanitize(value.doubleValue());
    }

    private double sanitize(double value) {
        return Double.isFinite(value) ? value : 0D;
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, sanitize(value)));
    }

    private void validateFeatureCount() {
        if (config.getInputSize() != FEATURE_COUNT) {
            throw new IllegalStateException("LSTM inputSize 必须为 " + FEATURE_COUNT
                    + "，当前配置为 " + config.getInputSize());
        }
        if (config.getTargetReturnScale() <= 0D) {
            throw new IllegalStateException("LSTM targetReturnScale 必须大于 0");
        }
        if (config.getMaxPanelSamples() <= 0 || config.getMaxSamplesPerStock() <= 0) {
            throw new IllegalStateException("LSTM 面板样本上限必须大于 0");
        }
        if (config.getStockEmbeddingBuckets() <= 1 || config.getIndustryEmbeddingBuckets() <= 1
                || config.getGroupHeadCount() <= 1) {
            throw new IllegalStateException("Embedding 哈希桶和分组 Head 数量必须大于 1");
        }
        if (config.getGroupHeadScale() < 0D || config.getResidualHeadScale() < 0D) {
            throw new IllegalStateException("分组和个股 Head 缩放系数不能小于 0");
        }
    }

    private record SeriesSamples(List<TrainingSample> allSamples) {
    }

    /**
     * 单条 LSTM 训练样本。
     *
     * @author mwangli
     * @since 2026-10-08
     */
    @Data
    @Builder
    public static class TrainingSample {
        /** 输入序列，形状为 sequenceLength x featureCount。 */
        private float[][] input;

        /** 缩放后的下一交易日收益率。 */
        private float returnTarget;

        /** 下一交易日上涨方向标签。 */
        private float directionTarget;

        /** 下一交易日下行幅度风险标签。 */
        private float downsideTarget;

        /** 样本所属股票代码，用于审计面板样本边界。 */
        private String stockCode;

        /** 标签对应的目标交易日，用于时间切分和防止未来数据泄漏。 */
        private LocalDate targetDate;
    }

    /**
     * 股票静态特征上下文。
     *
     * @author mwangli
     * @since 2026-10-08
     */
    @Data
    @Builder
    public static class StockContext {
        /** 股票代码。 */
        private String stockCode;

        /** 行业代码。 */
        private Integer industryCode;

        /** 股票所属市场，如 SH、SZ、BJ。 */
        private String market;

        /**
         * 构建只有股票代码的默认上下文。
         *
         * @param stockCode 股票代码
         * @return 默认上下文
         */
        public static StockContext basic(String stockCode) {
            return StockContext.builder().stockCode(stockCode == null ? "unknown" : stockCode).build();
        }
    }

    /**
     * 面板训练和验证数据。
     *
     * @author mwangli
     * @since 2026-10-08
     */
    @Data
    @Builder
    public static class ProcessedData {
        /** 训练样本。 */
        private List<TrainingSample> trainSamples;

        /** 验证样本。 */
        private List<TrainingSample> valSamples;

        /** 各股票样本数量。 */
        private Map<String, Integer> sampleCounts;

        /** 特征数量。 */
        private int featureCount;

        /** 输入序列长度。 */
        private int sequenceLength;

        /** 标签缩放值。 */
        private double targetReturnScale;

        /** 特征定义版本。 */
        private String featureVersion;

        /**
         * 获取训练输入。
         *
         * @return 三维训练输入
         */
        public float[][][] getTrainInputs() {
            return trainSamples.stream().map(TrainingSample::getInput).toArray(float[][][]::new);
        }

        /**
         * 获取训练标签。
         *
         * @return 每条样本包含收益率、方向概率和下行风险三个标签
         */
        public float[][] getTrainTargets() {
            return trainSamples.stream()
                    .map(this::toTargetVector)
                    .toArray(float[][]::new);
        }

        /**
         * 获取验证输入。
         *
         * @return 三维验证输入
         */
        public float[][][] getValInputs() {
            return valSamples.stream().map(TrainingSample::getInput).toArray(float[][][]::new);
        }

        /**
         * 获取验证标签。
         *
         * @return 每条样本包含收益率、方向概率和下行风险三个标签
         */
        public float[][] getValTargets() {
            return valSamples.stream()
                    .map(this::toTargetVector)
                    .toArray(float[][]::new);
        }

        private float[] toTargetVector(TrainingSample sample) {
            return new float[]{
                    sample.getReturnTarget(),
                    sample.getDirectionTarget(),
                    sample.getDownsideTarget()
            };
        }
    }

    /**
     * 单只股票最新预测输入。
     *
     * @author mwangli
     * @since 2026-10-08
     */
    @Data
    @Builder
    public static class PredictionInput {
        /** 预测输入特征。 */
        private float[][] input;

        /** 最新收盘价。 */
        private double lastClosePrice;

        /** 特征定义版本。 */
        private String featureVersion;
    }
}
// AI_GENERATE_END --------
