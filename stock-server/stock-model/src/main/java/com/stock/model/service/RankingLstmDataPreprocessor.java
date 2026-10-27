// AI_GENERATE_START--------
package com.stock.model.service;

import com.stock.data.dto.ModelStockDailyBarDto;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.model.config.LstmTrainingConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * `V6` 横截面 Ranking LSTM 数据预处理器。
 * 使用版本化模型数据生成可交易标签、稳定类别字典、全市场日期切分和训练区间标准化特征。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RankingLstmDataPreprocessor {

    /** 价格、横截面和新闻快照联合特征契约版本。 */
    public static final String FEATURE_VERSION = "tushare-free-temporal-news-v6-53d";

    /** `V6` 标签契约版本。 */
    public static final String LABEL_VERSION = "open-open-market-neutral-cost-v1";

    /** `V6` 时间切分契约版本。 */
    public static final String SPLIT_POLICY_VERSION = "global-walk-forward-purge-embargo-v1";

    /** `V6` 成本模型版本。 */
    public static final String COST_MODEL_VERSION = "round-trip-threshold-v1";

    /** 免费行情和股票池市场特征数量。 */
    public static final int MARKET_FEATURE_COUNT = 25;

    /** 价格、技术面和横截面连续特征数量。 */
    public static final int BASE_CONTINUOUS_FEATURE_COUNT = MARKET_FEATURE_COUNT;

    /** `temporal-news-v1` 新闻连续特征数量。 */
    public static final int NEWS_FEATURE_COUNT = NewsFeatureSnapshotService.FEATURE_COUNT;

    /** 连续特征总数。 */
    public static final int CONTINUOUS_FEATURE_COUNT = BASE_CONTINUOUS_FEATURE_COUNT + NEWS_FEATURE_COUNT;

    /** 总特征数量，末尾三列为股票、行业和市场字典索引。 */
    public static final int FEATURE_COUNT = CONTINUOUS_FEATURE_COUNT + 3;

    /** 正式模型代际。 */
    public static final String MODEL_GENERATION = "V6";

    /** 免费日线价格连续化口径。 */
    public static final String PRICE_BASIS = "NORMALIZED_PRE_CLOSE_V1";

    /** 市场基准构造口径。 */
    public static final String BENCHMARK_TYPE = "MARKET_EQUAL_WEIGHT";

    private static final String UNKNOWN_INDUSTRY = "UNKNOWN";
    private static final double MIN_VALUE = 1.0E-8D;

    private final LstmTrainingConfig config;
    private final TechnicalIndicatorService technicalIndicatorService;

    /**
     * 构建 `V6` 训练、验证和独立测试样本。
     *
     * @param bundle 版本化模型训练数据
     * @return 完成全市场切分和训练区间标准化的数据
     */
    public LstmDataPreprocessor.ProcessedData process(
            RankingLstmTrainingDataService.RankingTrainingDataBundle bundle) {
        validateConfiguration();
        Map<String, List<AdjustedBar>> adjustedSeries = buildAdjustedSeries(bundle.getPriceSeries());
        if (adjustedSeries.isEmpty()) {
            throw new IllegalStateException("价格连续化后没有可用于 V6 训练的股票序列");
        }
        Map<LocalDate, Double> marketBreadth = calculateMarketBreadth(adjustedSeries);
        MarketSeries marketSeries = buildMarketSeries(adjustedSeries);
        StableDictionaries dictionaries = buildDictionaries(adjustedSeries.keySet());

        List<ProvisionalSample> provisionalSamples = new ArrayList<>();
        for (Map.Entry<String, List<AdjustedBar>> entry : adjustedSeries.entrySet()) {
            buildStockSamples(entry.getKey(), entry.getValue(), marketSeries, marketBreadth, dictionaries,
                    bundle.getNewsFeatureSnapshots(), provisionalSamples);
        }
        if (provisionalSamples.isEmpty()) {
            throw new IllegalStateException("V6 数据门禁后没有可交易训练样本");
        }

        List<LstmDataPreprocessor.TrainingSample> samples = createTrainingSamples(provisionalSamples);
        samples.sort(Comparator.comparing(LstmDataPreprocessor.TrainingSample::getTargetDate)
                .thenComparing(LstmDataPreprocessor.TrainingSample::getStockCode));
        List<LstmDataPreprocessor.WalkForwardFold> walkForwardFolds = buildWalkForwardFolds(
                samples, config.getPurgeTradingDays() + config.getEmbargoTradingDays(), false);
        List<LstmDataPreprocessor.WalkForwardFold> strictIsolationFolds = buildWalkForwardFolds(
                samples, config.getPurgeTradingDays() + config.getStrictIsolationTradingDays(), true);
        if (walkForwardFolds.isEmpty()) {
            throw new IllegalStateException("V6 全市场样本无法生成 Walk-Forward 折叠");
        }
        LstmDataPreprocessor.WalkForwardFold latestFold = walkForwardFolds.get(walkForwardFolds.size() - 1);

        Map<String, Integer> sampleCounts = samples.stream().collect(Collectors.groupingBy(
                LstmDataPreprocessor.TrainingSample::getStockCode,
                LinkedHashMap::new, Collectors.summingInt(ignored -> 1)));
        log.info("V6 横截面样本构建完成: stocks={}, train={}, validation={}, test={}, "
                        + "trainRange={}..{}, validationRange={}..{}, testRange={}..{}",
                sampleCounts.size(), latestFold.getTrainSamples().size(), latestFold.getValSamples().size(),
                latestFold.getTestSamples().size(), latestFold.getTrainStartDate(), latestFold.getTrainEndDate(),
                latestFold.getValidationStartDate(), latestFold.getValidationEndDate(),
                latestFold.getTestStartDate(), latestFold.getTestEndDate());
        return LstmDataPreprocessor.ProcessedData.builder()
                .trainSamples(latestFold.getTrainSamples())
                .valSamples(latestFold.getValSamples())
                .testSamples(latestFold.getTestSamples())
                .sampleCounts(sampleCounts)
                .featureCount(FEATURE_COUNT)
                .sequenceLength(config.getSequenceLength())
                .targetReturnScale(config.getTargetReturnScale())
                .featureVersion(FEATURE_VERSION)
                .stockDictionary(dictionaries.stockDictionary())
                .industryDictionary(dictionaries.industryDictionary())
                .marketDictionary(dictionaries.marketDictionary())
                .featureMeans(latestFold.getFeatureMeans())
                .featureStdDeviations(latestFold.getFeatureStdDeviations())
                .trainStartDate(latestFold.getTrainStartDate())
                .trainEndDate(latestFold.getTrainEndDate())
                .validationStartDate(latestFold.getValidationStartDate())
                .validationEndDate(latestFold.getValidationEndDate())
                .testStartDate(latestFold.getTestStartDate())
                .testEndDate(latestFold.getTestEndDate())
                .walkForwardFolds(walkForwardFolds)
                .strictIsolationFolds(strictIsolationFolds)
                .build();
    }

    /**
     * 使用模型制品中的稳定字典和标准化参数构造最新推理输入。
     *
     * @param bundle 版本化模型数据
     * @param stockDictionary 股票稳定字典
     * @param industryDictionary 行业稳定字典
     * @param marketDictionary 市场稳定字典
     * @param featureMeans 训练窗口连续特征均值
     * @param featureStdDeviations 训练窗口连续特征标准差
     * @return 股票代码到最新推理输入的映射
     */
    public Map<String, LstmDataPreprocessor.PredictionInput> buildPredictionInputs(
            RankingLstmTrainingDataService.RankingTrainingDataBundle bundle,
            Map<String, Integer> stockDictionary,
            Map<String, Integer> industryDictionary,
            Map<String, Integer> marketDictionary,
            double[] featureMeans,
            double[] featureStdDeviations) {
        if (featureMeans == null || featureMeans.length != CONTINUOUS_FEATURE_COUNT
                || featureStdDeviations == null || featureStdDeviations.length != CONTINUOUS_FEATURE_COUNT) {
            throw new IllegalStateException("V6 模型制品中的标准化参数维度不正确");
        }
        Map<String, List<AdjustedBar>> adjustedSeries = buildAdjustedSeries(bundle.getPriceSeries());
        Map<LocalDate, Double> marketBreadth = calculateMarketBreadth(adjustedSeries);
        MarketSeries marketSeries = buildMarketSeries(adjustedSeries);
        StableDictionaries dictionaries = new StableDictionaries(
                stockDictionary, industryDictionary, marketDictionary);
        Map<String, LstmDataPreprocessor.PredictionInput> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<AdjustedBar>> entry : adjustedSeries.entrySet()) {
            List<AdjustedBar> bars = entry.getValue();
            if (bars.size() < config.getSequenceLength()) {
                continue;
            }
            AdjustedBar latest = bars.get(bars.size() - 1);
            String market = resolveMarket(entry.getKey());
            Map<String, double[]> indicators = technicalIndicatorService.calculateIndicators(
                    toStockPriceItemDtos(entry.getKey(), bars));
            float[][] input = new float[config.getSequenceLength()][FEATURE_COUNT];
            int start = bars.size() - config.getSequenceLength();
            for (int timeStep = 0; timeStep < config.getSequenceLength(); timeStep++) {
                int barIndex = start + timeStep;
                double[] features = buildContinuousFeatures(
                        entry.getKey(), bars, barIndex, indicators, marketSeries, marketBreadth,
                        bundle.getNewsFeatureSnapshots());
                for (int featureIndex = 0; featureIndex < CONTINUOUS_FEATURE_COUNT; featureIndex++) {
                    double deviation = featureStdDeviations[featureIndex] < 1.0E-6D
                            ? 1D : featureStdDeviations[featureIndex];
                    input[timeStep][featureIndex] = (float) clamp(
                            (features[featureIndex] - featureMeans[featureIndex]) / deviation, -8D, 8D);
                }
                input[timeStep][CONTINUOUS_FEATURE_COUNT] = dictionaries.stockIndex(entry.getKey());
                input[timeStep][CONTINUOUS_FEATURE_COUNT + 1] =
                        dictionaries.industryIndex(UNKNOWN_INDUSTRY);
                input[timeStep][CONTINUOUS_FEATURE_COUNT + 2] = dictionaries.marketIndex(market);
            }
            result.put(entry.getKey(), LstmDataPreprocessor.PredictionInput.builder()
                    .input(input)
                    .lastClosePrice(latest.rawClose())
                    .baseTradeDate(latest.tradeDate())
                    .featureVersion(FEATURE_VERSION)
                    .build());
        }
        return result;
    }

    private Map<String, List<AdjustedBar>> buildAdjustedSeries(
            Map<String, List<ModelStockDailyBarDto>> priceSeries) {
        Map<String, List<AdjustedBar>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<ModelStockDailyBarDto>> entry : priceSeries.entrySet()) {
            List<ModelStockDailyBarDto> sourceBars = entry.getValue();
            List<AdjustedBar> adjusted = new ArrayList<>();
            double cumulativeFactor = 1D;
            boolean complete = true;
            for (int index = 0; index < sourceBars.size(); index++) {
                ModelStockDailyBarDto bar = sourceBars.get(index);
                double rawClose = safe(bar.getClosePrice());
                if (index > 0) {
                    double previousRawClose = safe(sourceBars.get(index - 1).getClosePrice());
                    double preClose = safe(bar.getPreClosePrice());
                    if (previousRawClose <= MIN_VALUE || preClose <= MIN_VALUE) {
                        complete = false;
                        break;
                    }
                    cumulativeFactor *= previousRawClose / preClose;
                }
                adjusted.add(new AdjustedBar(
                        bar.getTradeDate(),
                        safe(bar.getOpenPrice()) * cumulativeFactor,
                        safe(bar.getHighPrice()) * cumulativeFactor,
                        safe(bar.getLowPrice()) * cumulativeFactor,
                        rawClose * cumulativeFactor,
                        rawClose,
                        safe(bar.getVolume()),
                        safe(bar.getAmount())));
            }
            if (complete && adjusted.size() >= config.getSequenceLength() + 2) {
                result.put(entry.getKey(), adjusted);
            } else {
                log.warn("跳过 pre_close 连续化数据不完整的 V6 股票序列: stockCode={}, validRows={}",
                        entry.getKey(), adjusted.size());
            }
        }
        return result;
    }

    private Map<LocalDate, Double> calculateMarketBreadth(Map<String, List<AdjustedBar>> adjustedSeries) {
        Map<LocalDate, int[]> counters = new HashMap<>();
        for (List<AdjustedBar> bars : adjustedSeries.values()) {
            for (int index = 1; index < bars.size(); index++) {
                AdjustedBar current = bars.get(index);
                AdjustedBar previous = bars.get(index - 1);
                int[] counter = counters.computeIfAbsent(current.tradeDate(), ignored -> new int[2]);
                counter[1]++;
                if (current.close() > previous.close()) {
                    counter[0]++;
                }
            }
        }
        Map<LocalDate, Double> result = new HashMap<>();
        counters.forEach((date, counter) -> result.put(
                date, counter[1] == 0 ? 0D : counter[0] / (double) counter[1]));
        return result;
    }

    private MarketSeries buildMarketSeries(Map<String, List<AdjustedBar>> adjustedSeries) {
        Map<LocalDate, List<Double>> closeReturnValues = new HashMap<>();
        Map<LocalDate, List<Double>> openToOpenReturnValues = new HashMap<>();
        for (List<AdjustedBar> bars : adjustedSeries.values()) {
            for (int index = 1; index < bars.size(); index++) {
                AdjustedBar previous = bars.get(index - 1);
                AdjustedBar current = bars.get(index);
                if (previous.close() > MIN_VALUE && current.close() > MIN_VALUE) {
                    closeReturnValues.computeIfAbsent(current.tradeDate(), ignored -> new ArrayList<>())
                            .add(current.close() / previous.close() - 1D);
                }
                if (previous.open() > MIN_VALUE && current.open() > MIN_VALUE) {
                    openToOpenReturnValues.computeIfAbsent(previous.tradeDate(), ignored -> new ArrayList<>())
                            .add(current.open() / previous.open() - 1D);
                }
            }
        }
        return new MarketSeries(averageByDate(closeReturnValues), averageByDate(openToOpenReturnValues));
    }

    private Map<LocalDate, Double> averageByDate(Map<LocalDate, List<Double>> valuesByDate) {
        Map<LocalDate, Double> result = new HashMap<>();
        valuesByDate.forEach((date, values) -> result.put(date,
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0D)));
        return result;
    }

    private StableDictionaries buildDictionaries(Set<String> stockCodes) {
        Set<String> markets = stockCodes.stream()
                .map(this::resolveMarket)
                .collect(Collectors.toCollection(TreeSet::new));
        return new StableDictionaries(dictionary(new TreeSet<>(stockCodes)),
                dictionary(Set.of()), dictionary(markets));
    }

    private Map<String, Integer> dictionary(Set<String> values) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("UNKNOWN", 0);
        int index = 1;
        for (String value : values) {
            result.put(value, index++);
        }
        return result;
    }

    private void buildStockSamples(
            String stockCode,
            List<AdjustedBar> bars,
            MarketSeries marketSeries,
            Map<LocalDate, Double> marketBreadth,
            StableDictionaries dictionaries,
            Map<String, double[]> newsFeatureSnapshots,
            List<ProvisionalSample> target) {
        List<StockPriceItemDto> technicalPrices = toStockPriceItemDtos(stockCode, bars);
        Map<String, double[]> indicators = technicalIndicatorService.calculateIndicators(technicalPrices);
        int sequenceLength = config.getSequenceLength();
        String market = resolveMarket(stockCode);
        for (int targetIndex = sequenceLength; targetIndex + 1 < bars.size(); targetIndex++) {
            AdjustedBar entry = bars.get(targetIndex);
            AdjustedBar exit = bars.get(targetIndex + 1);
            if (!isDerivedTradable(entry, exit)) {
                continue;
            }
            Double benchmarkReturn = marketSeries.openToOpenReturns().get(entry.tradeDate());
            if (benchmarkReturn == null) {
                continue;
            }
            double rawReturn = exit.open() / entry.open() - 1D;
            double excessReturn = rawReturn - benchmarkReturn;
            float[][] input = new float[sequenceLength][FEATURE_COUNT];
            int start = targetIndex - sequenceLength;
            for (int timeStep = 0; timeStep < sequenceLength; timeStep++) {
                int barIndex = start + timeStep;
                double[] features = buildContinuousFeatures(
                        stockCode, bars, barIndex, indicators, marketSeries, marketBreadth,
                        newsFeatureSnapshots);
                for (int featureIndex = 0; featureIndex < CONTINUOUS_FEATURE_COUNT; featureIndex++) {
                    input[timeStep][featureIndex] = (float) features[featureIndex];
                }
                input[timeStep][CONTINUOUS_FEATURE_COUNT] = dictionaries.stockIndex(stockCode);
                input[timeStep][CONTINUOUS_FEATURE_COUNT + 1] =
                        dictionaries.industryIndex(UNKNOWN_INDUSTRY);
                input[timeStep][CONTINUOUS_FEATURE_COUNT + 2] = dictionaries.marketIndex(market);
            }
            target.add(new ProvisionalSample(stockCode, UNKNOWN_INDUSTRY, entry.tradeDate(), input,
                    excessReturn));
        }
    }

    private List<StockPriceItemDto> toStockPriceItemDtos(String stockCode, List<AdjustedBar> bars) {
        List<StockPriceItemDto> result = new ArrayList<>(bars.size());
        for (AdjustedBar bar : bars) {
            StockPriceItemDto price = new StockPriceItemDto();
            price.setCode(stockCode);
            price.setDate(bar.tradeDate());
            price.setOpenPrice(BigDecimal.valueOf(bar.open()));
            price.setHighPrice(BigDecimal.valueOf(bar.high()));
            price.setLowPrice(BigDecimal.valueOf(bar.low()));
            price.setClosePrice(BigDecimal.valueOf(bar.close()));
            price.setVolume(BigDecimal.valueOf(bar.volume()));
            price.setAmount(BigDecimal.valueOf(bar.amount()));
            result.add(price);
        }
        return result;
    }

    private double[] buildContinuousFeatures(
            String stockCode,
            List<AdjustedBar> bars,
            int index,
            Map<String, double[]> indicators,
            MarketSeries marketSeries,
            Map<LocalDate, Double> marketBreadth,
            Map<String, double[]> newsFeatureSnapshots) {
        AdjustedBar current = bars.get(index);
        double previousClose = index == 0 ? current.close() : bars.get(index - 1).close();
        double benchmarkReturn = marketSeries.closeReturns().getOrDefault(current.tradeDate(), 0D);
        double stockReturn = ratio(current.close(), previousClose) - 1D;
        double[] feature = new double[CONTINUOUS_FEATURE_COUNT];
        feature[0] = clamp(ratio(current.open(), previousClose) - 1D);
        feature[1] = clamp(ratio(current.high(), previousClose) - 1D);
        feature[2] = clamp(ratio(current.low(), previousClose) - 1D);
        feature[3] = clamp(stockReturn);
        feature[4] = relativeLog(current.volume(), rollingMean(bars, index, 20, AdjustedBar::volume));
        feature[5] = relativeLog(current.amount(), rollingMean(bars, index, 20, AdjustedBar::amount));
        feature[6] = safeIndicator(indicators.get("RSI"), index) / 100D;
        feature[7] = safeDivide(safeIndicator(indicators.get("MACD"), index), current.close());
        feature[8] = clamp(ratio(safeIndicator(indicators.get("SMA"), index), current.close()) - 1D);
        feature[9] = clamp(ratio(safeIndicator(indicators.get("UpperBoll"), index), current.close()) - 1D);
        feature[10] = clamp(ratio(safeIndicator(indicators.get("LowerBoll"), index), current.close()) - 1D);
        feature[11] = safeDivide(safeIndicator(indicators.get("OBV"), index),
                rollingSum(bars, index, 20, AdjustedBar::volume));
        feature[12] = periodReturn(bars, index, 5);
        feature[13] = periodReturn(bars, index, 10);
        feature[14] = periodReturn(bars, index, 20);
        feature[15] = periodReturn(bars, index, 60);
        feature[16] = volatility(bars, index, 5);
        feature[17] = volatility(bars, index, 10);
        feature[18] = volatility(bars, index, 20);
        feature[19] = volatility(bars, index, 60);
        feature[20] = benchmarkReturn;
        feature[21] = clamp(stockReturn - benchmarkReturn);
        feature[22] = marketPeriodReturn(marketSeries.closeReturns(), current.tradeDate(), 20);
        feature[23] = marketBreadth.getOrDefault(current.tradeDate(), 0D);
        feature[24] = current.amount() > 0D && current.volume() > 0D ? 1D : 0D;
        double[] newsFeatures = newsFeatureSnapshots == null ? null
                : newsFeatureSnapshots.get(key(stockCode, current.tradeDate()));
        if (newsFeatures == null || newsFeatures.length != NEWS_FEATURE_COUNT) {
            throw new IllegalStateException("缺少 temporal-news-v1 快照: "
                    + key(stockCode, current.tradeDate()));
        }
        System.arraycopy(newsFeatures, 0, feature, BASE_CONTINUOUS_FEATURE_COUNT, NEWS_FEATURE_COUNT);
        return feature;
    }

    private List<LstmDataPreprocessor.TrainingSample> createTrainingSamples(
            List<ProvisionalSample> provisionalSamples) {
        List<LstmDataPreprocessor.TrainingSample> result = new ArrayList<>(provisionalSamples.size());
        for (ProvisionalSample sample : provisionalSamples) {
            double neutralReturn = sample.excessReturn();
            float scaledReturn = (float) clamp(
                    neutralReturn / config.getTargetReturnScale(), -1D, 1D);
            float direction = neutralReturn > config.getCostThreshold()
                    ? 1F : neutralReturn < -config.getCostThreshold() ? 0F : 0.5F;
            float downside = (float) clamp(-neutralReturn / config.getTargetReturnScale(), 0D, 1D);
            result.add(LstmDataPreprocessor.TrainingSample.builder()
                    .input(sample.input())
                    .rankingTarget(scaledReturn)
                    .returnTarget(scaledReturn)
                    .directionTarget(direction)
                    .downsideTarget(downside)
                    .stockCode(sample.stockCode())
                    .industryCode(sample.industryCode())
                    .targetDate(sample.targetDate())
                    .build());
        }
        return result;
    }

    private List<LstmDataPreprocessor.WalkForwardFold> buildWalkForwardFolds(
            List<LstmDataPreprocessor.TrainingSample> samples, int boundaryGap, boolean strictIsolation) {
        List<LocalDate> dates = samples.stream().map(LstmDataPreprocessor.TrainingSample::getTargetDate)
                .distinct().sorted().toList();
        if (dates.size() < boundaryGap * 2 + 10) {
            throw new IllegalStateException("V6 全市场目标交易日数量不足，无法执行 purge 和 embargo");
        }
        List<SplitResult> splits = new ArrayList<>();
        LocalDate foldEnd = dates.get(dates.size() - 1);
        while (true) {
            SplitResult split = splitByGlobalDate(samples, dates, foldEnd, boundaryGap);
            if (split == null) {
                break;
            }
            splits.add(split);
            foldEnd = foldEnd.minusMonths(config.getWalkForwardStepMonths());
        }
        java.util.Collections.reverse(splits);
        List<LstmDataPreprocessor.WalkForwardFold> folds = new ArrayList<>();
        for (int index = 0; index < splits.size(); index++) {
            SplitResult split = splits.get(index);
            List<LstmDataPreprocessor.TrainingSample> train = copySamples(split.trainSamples());
            List<LstmDataPreprocessor.TrainingSample> validation = copySamples(split.validationSamples());
            List<LstmDataPreprocessor.TrainingSample> test = copySamples(split.testSamples());
            NormalizationStats normalization = normalizeByTrainingWindow(train, validation, test);
            folds.add(LstmDataPreprocessor.WalkForwardFold.builder()
                    .foldNumber(index + 1)
                    .strictIsolation(strictIsolation)
                    .trainSamples(train)
                    .valSamples(validation)
                    .testSamples(test)
                    .featureMeans(normalization.means())
                    .featureStdDeviations(normalization.standardDeviations())
                    .trainStartDate(split.trainStartDate())
                    .trainEndDate(split.trainEndDate())
                    .validationStartDate(split.validationStartDate())
                    .validationEndDate(split.validationEndDate())
                    .testStartDate(split.testStartDate())
                    .testEndDate(split.testEndDate())
                    .build());
        }
        return folds;
    }

    private SplitResult splitByGlobalDate(
            List<LstmDataPreprocessor.TrainingSample> samples,
            List<LocalDate> dates,
            LocalDate requestedFoldEnd,
            int boundaryGap) {
        int foldEndIndex = lastIndexOnOrBefore(dates, requestedFoldEnd);
        if (foldEndIndex < 0) {
            return null;
        }
        LocalDate datasetEnd = dates.get(foldEndIndex);
        LocalDate requestedTestStart = datasetEnd.minusMonths(config.getTestWindowMonths()).plusDays(1);
        int testStartIndex = firstIndexOnOrAfter(dates, requestedTestStart);
        if (testStartIndex > foldEndIndex) {
            return null;
        }
        LocalDate requestedValidationStart = dates.get(testStartIndex)
                .minusMonths(config.getValidationWindowMonths());
        int validationStartIndex = firstIndexOnOrAfter(dates, requestedValidationStart);
        int trainEndIndex = validationStartIndex - boundaryGap - 1;
        int validationEndIndex = testStartIndex - boundaryGap - 1;
        if (trainEndIndex < 0 || validationEndIndex < validationStartIndex || testStartIndex >= dates.size()) {
            return null;
        }
        LocalDate trainEnd = dates.get(trainEndIndex);
        LocalDate trainStart = trainEnd.minusYears(config.getTrainingWindowYears()).plusDays(1);
        LocalDate validationStart = dates.get(validationStartIndex);
        LocalDate validationEnd = dates.get(validationEndIndex);
        LocalDate testStart = dates.get(testStartIndex);
        List<LstmDataPreprocessor.TrainingSample> train = new ArrayList<>();
        List<LstmDataPreprocessor.TrainingSample> validation = new ArrayList<>();
        List<LstmDataPreprocessor.TrainingSample> test = new ArrayList<>();
        for (LstmDataPreprocessor.TrainingSample sample : samples) {
            LocalDate date = sample.getTargetDate();
            if (!date.isBefore(trainStart) && !date.isAfter(trainEnd)) {
                train.add(sample);
            } else if (!date.isBefore(validationStart) && !date.isAfter(validationEnd)) {
                validation.add(sample);
            } else if (!date.isBefore(testStart) && !date.isAfter(datasetEnd)) {
                test.add(sample);
            }
        }
        if (train.isEmpty() || validation.isEmpty() || test.isEmpty()) {
            return null;
        }
        return new SplitResult(train, validation, test, train.get(0).getTargetDate(), trainEnd,
                validationStart, validationEnd, testStart, datasetEnd);
    }

    private List<LstmDataPreprocessor.TrainingSample> copySamples(
            List<LstmDataPreprocessor.TrainingSample> source) {
        List<LstmDataPreprocessor.TrainingSample> result = new ArrayList<>(source.size());
        for (LstmDataPreprocessor.TrainingSample sample : source) {
            float[][] input = new float[sample.getInput().length][];
            for (int index = 0; index < sample.getInput().length; index++) {
                input[index] = sample.getInput()[index].clone();
            }
            result.add(LstmDataPreprocessor.TrainingSample.builder()
                    .input(input)
                    .rankingTarget(sample.getRankingTarget())
                    .returnTarget(sample.getReturnTarget())
                    .directionTarget(sample.getDirectionTarget())
                    .downsideTarget(sample.getDownsideTarget())
                    .stockCode(sample.getStockCode())
                    .industryCode(sample.getIndustryCode())
                    .targetDate(sample.getTargetDate())
                    .build());
        }
        return result;
    }

    private NormalizationStats normalizeByTrainingWindow(
            List<LstmDataPreprocessor.TrainingSample> train,
            List<LstmDataPreprocessor.TrainingSample> validation,
            List<LstmDataPreprocessor.TrainingSample> test) {
        double[] means = new double[CONTINUOUS_FEATURE_COUNT];
        double[] standardDeviations = new double[CONTINUOUS_FEATURE_COUNT];
        long observations = 0L;
        for (LstmDataPreprocessor.TrainingSample sample : train) {
            for (float[] row : sample.getInput()) {
                for (int index = 0; index < CONTINUOUS_FEATURE_COUNT; index++) {
                    means[index] += row[index];
                }
                observations++;
            }
        }
        for (int index = 0; index < means.length; index++) {
            means[index] /= Math.max(1L, observations);
        }
        for (LstmDataPreprocessor.TrainingSample sample : train) {
            for (float[] row : sample.getInput()) {
                for (int index = 0; index < CONTINUOUS_FEATURE_COUNT; index++) {
                    standardDeviations[index] += Math.pow(row[index] - means[index], 2D);
                }
            }
        }
        for (int index = 0; index < standardDeviations.length; index++) {
            standardDeviations[index] = Math.sqrt(standardDeviations[index] / Math.max(1L, observations));
            if (standardDeviations[index] < 1.0E-6D) {
                standardDeviations[index] = 1D;
            }
        }
        normalize(train, means, standardDeviations);
        normalize(validation, means, standardDeviations);
        normalize(test, means, standardDeviations);
        return new NormalizationStats(means, standardDeviations);
    }

    private void normalize(
            List<LstmDataPreprocessor.TrainingSample> samples, double[] means, double[] deviations) {
        for (LstmDataPreprocessor.TrainingSample sample : samples) {
            for (float[] row : sample.getInput()) {
                for (int index = 0; index < CONTINUOUS_FEATURE_COUNT; index++) {
                    row[index] = (float) clamp((row[index] - means[index]) / deviations[index], -8D, 8D);
                }
            }
        }
    }

    private boolean isDerivedTradable(AdjustedBar entry, AdjustedBar exit) {
        return entry.open() > MIN_VALUE && exit.open() > MIN_VALUE
                && entry.volume() > 0D && entry.amount() > 0D
                && exit.volume() > 0D && exit.amount() > 0D;
    }

    private String resolveMarket(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return "UNKNOWN";
        }
        String code = stockCode.trim();
        if (code.startsWith("6")) {
            return "SH";
        }
        if (code.startsWith("8") || code.startsWith("4")) {
            return "BJ";
        }
        return "SZ";
    }

    private double marketPeriodReturn(
            Map<LocalDate, Double> dailyReturns, LocalDate date, int period) {
        List<Double> values = dailyReturns.entrySet().stream()
                .filter(entry -> !entry.getKey().isAfter(date))
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();
        if (values.size() < period) {
            return 0D;
        }
        double compounded = 1D;
        for (int index = values.size() - period; index < values.size(); index++) {
            compounded *= 1D + values.get(index);
        }
        return clamp(compounded - 1D);
    }

    private double periodReturn(List<AdjustedBar> bars, int index, int period) {
        return index < period ? 0D : clamp(ratio(bars.get(index).close(), bars.get(index - period).close()) - 1D);
    }

    private double volatility(List<AdjustedBar> bars, int index, int period) {
        int start = Math.max(1, index - period + 1);
        int count = index - start + 1;
        if (count <= 1) {
            return 0D;
        }
        double mean = 0D;
        for (int cursor = start; cursor <= index; cursor++) {
            mean += ratio(bars.get(cursor).close(), bars.get(cursor - 1).close()) - 1D;
        }
        mean /= count;
        double variance = 0D;
        for (int cursor = start; cursor <= index; cursor++) {
            double value = ratio(bars.get(cursor).close(), bars.get(cursor - 1).close()) - 1D;
            variance += Math.pow(value - mean, 2D);
        }
        return Math.sqrt(variance / count);
    }

    private double rollingMean(
            List<AdjustedBar> bars, int index, int period, Function<AdjustedBar, Double> getter) {
        int start = Math.max(0, index - period + 1);
        return rollingSum(bars, index, period, getter) / Math.max(1, index - start + 1);
    }

    private double rollingSum(
            List<AdjustedBar> bars, int index, int period, Function<AdjustedBar, Double> getter) {
        int start = Math.max(0, index - period + 1);
        double total = 0D;
        for (int cursor = start; cursor <= index; cursor++) {
            total += Math.max(0D, getter.apply(bars.get(cursor)));
        }
        return total;
    }

    private double relativeLog(double value, double mean) {
        return clamp(Math.log((Math.max(0D, value) + 1D) / (Math.max(0D, mean) + 1D)) / 5D);
    }

    private double safeIndicator(double[] values, int index) {
        return values == null || index < 0 || index >= values.length || !Double.isFinite(values[index])
                ? 0D : values[index];
    }

    private int firstIndexOnOrAfter(List<LocalDate> dates, LocalDate requested) {
        for (int index = 0; index < dates.size(); index++) {
            if (!dates.get(index).isBefore(requested)) {
                return index;
            }
        }
        return dates.size() - 1;
    }

    private int lastIndexOnOrBefore(List<LocalDate> dates, LocalDate requested) {
        for (int index = dates.size() - 1; index >= 0; index--) {
            if (!dates.get(index).isAfter(requested)) {
                return index;
            }
        }
        return -1;
    }

    private String key(String code, LocalDate date) {
        return String.valueOf(code) + "|" + date;
    }

    private double safe(BigDecimal value) {
        return value == null || !Double.isFinite(value.doubleValue()) ? 0D : value.doubleValue();
    }

    private double ratio(double value, double reference) {
        return Math.abs(reference) <= MIN_VALUE ? 1D : value / reference;
    }

    private double safeDivide(double value, double divisor) {
        return Math.abs(divisor) <= MIN_VALUE ? 0D : value / divisor;
    }

    private double clamp(double value) {
        return clamp(value, -1D, 1D);
    }

    private double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, Double.isFinite(value) ? value : 0D));
    }

    private void validateConfiguration() {
        if (config.getInputSize() != FEATURE_COUNT) {
            throw new IllegalStateException("v6 LSTM inputSize 必须为 " + FEATURE_COUNT
                    + "，当前配置为 " + config.getInputSize());
        }
        if (config.getTargetReturnScale() <= 0D || config.getCostThreshold() < 0D) {
            throw new IllegalStateException("V6 收益缩放值必须大于 0，成本阈值不能小于 0");
        }
        if (config.getTrainingWindowYears() <= 0 || config.getValidationWindowMonths() <= 0
                || config.getTestWindowMonths() <= 0 || config.getWalkForwardStepMonths() <= 0
                || config.getEmbargoTradingDays() < 0 || config.getPurgeTradingDays() < 0
                || config.getStrictIsolationTradingDays() < 0) {
            throw new IllegalStateException("V6 Walk-Forward 时间窗口配置无效");
        }
    }

    private record AdjustedBar(
            LocalDate tradeDate,
            double open,
            double high,
            double low,
            double close,
            double rawClose,
            double volume,
            double amount) {
    }

    private record MarketSeries(
            Map<LocalDate, Double> closeReturns,
            Map<LocalDate, Double> openToOpenReturns) {
    }

    private record ProvisionalSample(
            String stockCode,
            String industryCode,
            LocalDate targetDate,
            float[][] input,
            double excessReturn) {
    }

    private record StableDictionaries(
            Map<String, Integer> stockDictionary,
            Map<String, Integer> industryDictionary,
            Map<String, Integer> marketDictionary) {

        private int stockIndex(String value) {
            return stockDictionary.getOrDefault(value, 0);
        }

        private int industryIndex(String value) {
            return industryDictionary.getOrDefault(value, 0);
        }

        private int marketIndex(String value) {
            return marketDictionary.getOrDefault(value, 0);
        }
    }

    private record SplitResult(
            List<LstmDataPreprocessor.TrainingSample> trainSamples,
            List<LstmDataPreprocessor.TrainingSample> validationSamples,
            List<LstmDataPreprocessor.TrainingSample> testSamples,
            LocalDate trainStartDate,
            LocalDate trainEndDate,
            LocalDate validationStartDate,
            LocalDate validationEndDate,
            LocalDate testStartDate,
            LocalDate testEndDate) {
    }

    private record NormalizationStats(double[] means, double[] standardDeviations) {
    }
}
// AI_GENERATE_END--------
