// AI_GENERATE_START-------------
package com.stock.model.service;

import com.stock.data.dto.ModelStockDailyBarDto;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelFeatureMetadata;
import com.stock.model.dto.RankingSampleSelectionPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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

    private static final String UNKNOWN_INDUSTRY = "UNKNOWN";
    private static final double MIN_VALUE = 1.0E-8D;

    private final LstmTrainingConfig config;
    private final TechnicalIndicatorService technicalIndicatorService;

    /**
     * 在加载新闻特征前固定训练样本集合，并展开入选样本的序列新闻键。
     *
     * @param priceSeries 股票代码到按交易日升序行情的映射
     * @param featureMetadata 本次训练的特征元数据
     * @return 可由正式预处理直接复用的样本选择计划
     */
    public RankingSampleSelectionPlan planSampleSelection(
            Map<String, List<ModelStockDailyBarDto>> priceSeries,
            ModelFeatureMetadata featureMetadata) {
        validateConfiguration(featureMetadata);
        Map<String, List<AdjustedBar>> adjustedSeries = buildAdjustedSeries(priceSeries);
        if (adjustedSeries.isEmpty()) {
            throw new IllegalStateException("价格连续化后没有可用于 V6 训练的股票序列");
        }
        MarketSeries marketSeries = buildMarketSeries(adjustedSeries);
        Map<String, Set<Integer>> selectedTargetIndices = selectSampleTargetIndices(
                adjustedSeries, marketSeries);
        Map<String, Set<Integer>> immutableSelection = new LinkedHashMap<>();
        selectedTargetIndices.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> immutableSelection.put(entry.getKey(), Set.copyOf(entry.getValue())));

        Set<String> requiredKeys = new TreeSet<>();
        Set<String> requiredStockCodes = new TreeSet<>();
        if (featureMetadata.isNewsFeaturesEnabled()) {
            int sequenceLength = config.getSequenceLength();
            for (Map.Entry<String, Set<Integer>> entry : immutableSelection.entrySet()) {
                List<AdjustedBar> bars = adjustedSeries.get(entry.getKey());
                for (Integer targetIndex : entry.getValue()) {
                    int start = targetIndex - sequenceLength;
                    for (int index = start; index < targetIndex; index++) {
                        requiredKeys.add(key(entry.getKey(), bars.get(index).tradeDate()));
                        requiredStockCodes.add(entry.getKey());
                    }
                }
            }
        }
        LocalDate startDate = requiredKeys.stream().map(this::dateFromKey)
                .min(LocalDate::compareTo).orElse(null);
        LocalDate endDate = requiredKeys.stream().map(this::dateFromKey)
                .max(LocalDate::compareTo).orElse(null);
        int selectedTargetCount = immutableSelection.values().stream().mapToInt(Set::size).sum();
        return RankingSampleSelectionPlan.builder()
                .selectedTargetIndices(Map.copyOf(immutableSelection))
                .requiredNewsSnapshotKeys(Set.copyOf(requiredKeys))
                .requiredStockCodes(Set.copyOf(requiredStockCodes))
                .startDate(startDate)
                .endDate(endDate)
                .selectedTargetCount(selectedTargetCount)
                .uniqueSequenceDayCount(requiredKeys.size())
                .build();
    }

    /**
     * 构建 `V6` 训练、验证和独立测试样本。
     *
     * @param bundle 版本化模型训练数据
     * @return 完成全市场切分和训练区间标准化的数据
     */
    public LstmDataPreprocessor.ProcessedData process(
            RankingLstmTrainingDataService.RankingTrainingDataBundle bundle) {
        ModelFeatureMetadata featureMetadata = bundle.getFeatureMetadata();
        validateConfiguration(featureMetadata);
        Map<String, List<AdjustedBar>> adjustedSeries = buildAdjustedSeries(bundle.getPriceSeries());
        if (adjustedSeries.isEmpty()) {
            throw new IllegalStateException("价格连续化后没有可用于 V6 训练的股票序列");
        }
        Map<LocalDate, Double> marketBreadth = calculateMarketBreadth(adjustedSeries);
        MarketSeries marketSeries = buildMarketSeries(adjustedSeries);
        StableDictionaries dictionaries = buildDictionaries(adjustedSeries.keySet());
        RankingSampleSelectionPlan selectionPlan = bundle.getSampleSelectionPlan();
        Map<String, Set<Integer>> selectedTargetIndices = selectionPlan == null
                ? selectSampleTargetIndices(adjustedSeries, marketSeries)
                : selectionPlan.getSelectedTargetIndices();
        if (selectedTargetIndices == null || selectedTargetIndices.isEmpty()) {
            throw new IllegalStateException("V6 样本选择计划为空");
        }

        List<ProvisionalSample> provisionalSamples = new ArrayList<>();
        for (Map.Entry<String, List<AdjustedBar>> entry : adjustedSeries.entrySet()) {
            Set<Integer> stockTargetIndices = selectedTargetIndices.get(entry.getKey());
            if (stockTargetIndices == null || stockTargetIndices.isEmpty()) {
                continue;
            }
            buildStockSamples(entry.getKey(), entry.getValue(), marketSeries, marketBreadth, dictionaries,
                    bundle.getNewsFeatureSnapshots(), featureMetadata, stockTargetIndices, provisionalSamples);
        }
        if (provisionalSamples.isEmpty()) {
            throw new IllegalStateException("V6 数据门禁后没有可交易训练样本");
        }

        List<LstmDataPreprocessor.TrainingSample> samples = createTrainingSamples(provisionalSamples);
        samples.sort(Comparator.comparing(LstmDataPreprocessor.TrainingSample::getTargetDate)
                .thenComparing(LstmDataPreprocessor.TrainingSample::getStockCode));
        List<LstmDataPreprocessor.WalkForwardFold> walkForwardFolds = buildWalkForwardBoundaries(
                samples, config.getPurgeTradingDays() + config.getEmbargoTradingDays(), false);
        List<LstmDataPreprocessor.WalkForwardFold> strictIsolationFolds = buildWalkForwardBoundaries(
                samples, config.getPurgeTradingDays() + config.getStrictIsolationTradingDays(), true);
        if (walkForwardFolds.isEmpty()) {
            throw new IllegalStateException("V6 全市场样本无法生成 Walk-Forward 折叠");
        }
        LstmDataPreprocessor.WalkForwardFold latestFold = walkForwardFolds.get(walkForwardFolds.size() - 1);

        Map<String, Integer> sampleCounts = samples.stream().collect(Collectors.groupingBy(
                LstmDataPreprocessor.TrainingSample::getStockCode,
                LinkedHashMap::new, Collectors.summingInt(ignored -> 1)));
        log.info("V6 横截面原始样本构建完成: stocks={}, total={}, train={}, validation={}, test={}, "
                        + "trainRange={}..{}, validationRange={}..{}, testRange={}..{}",
                sampleCounts.size(), samples.size(), countSamples(samples,
                        latestFold.getTrainStartDate(), latestFold.getTrainEndDate()),
                countSamples(samples, latestFold.getValidationStartDate(), latestFold.getValidationEndDate()),
                countSamples(samples, latestFold.getTestStartDate(), latestFold.getTestEndDate()),
                latestFold.getTrainStartDate(), latestFold.getTrainEndDate(),
                latestFold.getValidationStartDate(), latestFold.getValidationEndDate(),
                latestFold.getTestStartDate(), latestFold.getTestEndDate());
        return LstmDataPreprocessor.ProcessedData.builder()
                .allSamples(samples)
                .sampleCounts(sampleCounts)
                .featureCount(featureMetadata.inputSize())
                .sequenceLength(config.getSequenceLength())
                .targetReturnScale(config.getTargetReturnScale())
                .featureVersion(featureMetadata.getFeatureVersion())
                .featureMetadata(featureMetadata)
                .stockDictionary(dictionaries.stockDictionary())
                .industryDictionary(dictionaries.industryDictionary())
                .marketDictionary(dictionaries.marketDictionary())
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
     * 将快照中的唯一原始样本按最新主策略边界重建为可训练视图。
     * 其余 Walk-Forward 折叠保留日期边界，由训练评估阶段逐折按需生成。
     *
     * @param rawData 仅包含原始样本、折叠边界和特征元数据的快照数据
     * @return 已生成主训练、验证和测试样本，并保留原始样本与折叠边界的数据
     */
    public LstmDataPreprocessor.ProcessedData materializeTrainingViews(
            LstmDataPreprocessor.ProcessedData rawData) {
        if (rawData == null || rawData.getAllSamples() == null || rawData.getAllSamples().isEmpty()) {
            throw new IllegalStateException("训练快照缺少唯一原始样本");
        }
        ModelFeatureMetadata featureMetadata = rawData.getFeatureMetadata();
        validateConfiguration(featureMetadata);
        validateRawSamples(rawData, featureMetadata);
        List<LstmDataPreprocessor.WalkForwardFold> walkForwardBoundaries = rawData.getWalkForwardFolds();
        if (walkForwardBoundaries == null || walkForwardBoundaries.isEmpty()) {
            throw new IllegalStateException("训练快照缺少主策略 Walk-Forward 边界");
        }
        LstmDataPreprocessor.WalkForwardFold latestBoundary =
                walkForwardBoundaries.get(walkForwardBoundaries.size() - 1);
        LstmDataPreprocessor.WalkForwardFold latestFold = materializeFold(rawData, latestBoundary);
        return LstmDataPreprocessor.ProcessedData.builder()
                .allSamples(rawData.getAllSamples())
                .trainSamples(latestFold.getTrainSamples())
                .valSamples(latestFold.getValSamples())
                .testSamples(latestFold.getTestSamples())
                .sampleCounts(rawData.getSampleCounts())
                .featureCount(rawData.getFeatureCount())
                .sequenceLength(rawData.getSequenceLength())
                .targetReturnScale(rawData.getTargetReturnScale())
                .featureVersion(rawData.getFeatureVersion())
                .featureMetadata(featureMetadata)
                .sampleSetHash(rawData.getSampleSetHash())
                .stockDictionary(rawData.getStockDictionary())
                .industryDictionary(rawData.getIndustryDictionary())
                .marketDictionary(rawData.getMarketDictionary())
                .featureMeans(latestFold.getFeatureMeans())
                .featureStdDeviations(latestFold.getFeatureStdDeviations())
                .trainStartDate(latestFold.getTrainStartDate())
                .trainEndDate(latestFold.getTrainEndDate())
                .validationStartDate(latestFold.getValidationStartDate())
                .validationEndDate(latestFold.getValidationEndDate())
                .testStartDate(latestFold.getTestStartDate())
                .testEndDate(latestFold.getTestEndDate())
                .walkForwardFolds(walkForwardBoundaries)
                .strictIsolationFolds(rawData.getStrictIsolationFolds())
                .build();
    }

    /**
     * 按单个 Walk-Forward 日期边界生成标准化训练、验证和测试样本。
     * 该方法用于训练阶段逐折评估，调用方不应长期持有返回折叠。
     *
     * @param data 包含唯一原始样本和特征元数据的快照数据
     * @param boundary 待生成的折叠日期边界
     * @return 仅使用该折训练窗口拟合标准化参数的完整折叠
     */
    public LstmDataPreprocessor.WalkForwardFold materializeFold(
            LstmDataPreprocessor.ProcessedData data,
            LstmDataPreprocessor.WalkForwardFold boundary) {
        if (data == null || data.getAllSamples() == null || data.getAllSamples().isEmpty()) {
            throw new IllegalStateException("训练快照缺少按需生成折叠所需的原始样本");
        }
        ModelFeatureMetadata featureMetadata = data.getFeatureMetadata();
        validateConfiguration(featureMetadata);
        validateFoldBoundary(boundary);
        return materializeFold(data.getAllSamples(), boundary, featureMetadata.continuousFeatureCount());
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
        ModelFeatureMetadata featureMetadata = bundle.getFeatureMetadata();
        validateConfiguration(featureMetadata);
        int continuousFeatureCount = featureMetadata.continuousFeatureCount();
        if (featureMeans == null || featureMeans.length != continuousFeatureCount
                || featureStdDeviations == null || featureStdDeviations.length != continuousFeatureCount) {
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
            float[][] input = new float[config.getSequenceLength()][featureMetadata.inputSize()];
            int start = bars.size() - config.getSequenceLength();
            for (int timeStep = 0; timeStep < config.getSequenceLength(); timeStep++) {
                int barIndex = start + timeStep;
                double[] features = buildContinuousFeatures(
                        entry.getKey(), bars, barIndex, indicators, marketSeries, marketBreadth,
                        bundle.getNewsFeatureSnapshots(), featureMetadata);
                for (int featureIndex = 0; featureIndex < continuousFeatureCount; featureIndex++) {
                    double deviation = featureStdDeviations[featureIndex] < 1.0E-6D
                            ? 1D : featureStdDeviations[featureIndex];
                    input[timeStep][featureIndex] = (float) clamp(
                            (features[featureIndex] - featureMeans[featureIndex]) / deviation, -8D, 8D);
                }
                input[timeStep][continuousFeatureCount] = dictionaries.stockIndex(entry.getKey());
                input[timeStep][continuousFeatureCount + 1] =
                        dictionaries.industryIndex(UNKNOWN_INDUSTRY);
                input[timeStep][continuousFeatureCount + 2] = dictionaries.marketIndex(market);
            }
            result.put(entry.getKey(), LstmDataPreprocessor.PredictionInput.builder()
                    .input(input)
                    .lastClosePrice(latest.rawClose())
                    .baseTradeDate(latest.tradeDate())
                    .featureVersion(featureMetadata.getFeatureVersion())
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
        Map<LocalDate, Double> closeReturns = averageByDate(closeReturnValues);
        return new MarketSeries(closeReturns, averageByDate(openToOpenReturnValues),
                rollingMarketReturns(closeReturns, 20));
    }

    private Map<LocalDate, Double> rollingMarketReturns(
            Map<LocalDate, Double> dailyReturns, int period) {
        List<Map.Entry<LocalDate, Double>> ordered = dailyReturns.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        Map<LocalDate, Double> result = new HashMap<>();
        for (int index = 0; index < ordered.size(); index++) {
            if (index + 1 < period) {
                result.put(ordered.get(index).getKey(), 0D);
                continue;
            }
            double compounded = 1D;
            for (int cursor = index - period + 1; cursor <= index; cursor++) {
                compounded *= 1D + ordered.get(cursor).getValue();
            }
            result.put(ordered.get(index).getKey(), clamp(compounded - 1D));
        }
        return result;
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
            ModelFeatureMetadata featureMetadata,
            Set<Integer> selectedTargetIndices,
            List<ProvisionalSample> target) {
        List<StockPriceItemDto> technicalPrices = toStockPriceItemDtos(stockCode, bars);
        Map<String, double[]> indicators = technicalIndicatorService.calculateIndicators(technicalPrices);
        int sequenceLength = config.getSequenceLength();
        String market = resolveMarket(stockCode);
        for (int targetIndex = sequenceLength; targetIndex + 1 < bars.size(); targetIndex++) {
            if (!selectedTargetIndices.contains(targetIndex)) {
                continue;
            }
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
            int continuousFeatureCount = featureMetadata.continuousFeatureCount();
            float[][] input = new float[sequenceLength][featureMetadata.inputSize()];
            int start = targetIndex - sequenceLength;
            for (int timeStep = 0; timeStep < sequenceLength; timeStep++) {
                int barIndex = start + timeStep;
                double[] features = buildContinuousFeatures(
                        stockCode, bars, barIndex, indicators, marketSeries, marketBreadth,
                        newsFeatureSnapshots, featureMetadata);
                for (int featureIndex = 0; featureIndex < continuousFeatureCount; featureIndex++) {
                    input[timeStep][featureIndex] = (float) features[featureIndex];
                }
                input[timeStep][continuousFeatureCount] = dictionaries.stockIndex(stockCode);
                input[timeStep][continuousFeatureCount + 1] =
                        dictionaries.industryIndex(UNKNOWN_INDUSTRY);
                input[timeStep][continuousFeatureCount + 2] = dictionaries.marketIndex(market);
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
            Map<String, double[]> newsFeatureSnapshots,
            ModelFeatureMetadata featureMetadata) {
        AdjustedBar current = bars.get(index);
        double previousClose = index == 0 ? current.close() : bars.get(index - 1).close();
        double benchmarkReturn = marketSeries.closeReturns().getOrDefault(current.tradeDate(), 0D);
        double stockReturn = ratio(current.close(), previousClose) - 1D;
        double[] feature = new double[featureMetadata.continuousFeatureCount()];
        feature[0] = clamp(ratio(current.open(), previousClose) - 1D);
        feature[1] = clamp(ratio(current.close(), current.open()) - 1D);
        feature[2] = clamp(safeDivide(current.high() - current.low(), previousClose));
        feature[3] = clamp(safeDivide(current.close() - current.low(), current.high() - current.low()), 0D, 1D);
        feature[4] = relativeLog(current.amount(), rollingMean(bars, index, 20, AdjustedBar::amount));
        feature[5] = safeIndicator(indicators.get("RSI"), index) / 100D;
        feature[6] = periodReturn(bars, index, 5);
        feature[7] = periodReturn(bars, index, 20);
        feature[8] = periodReturn(bars, index, 60);
        feature[9] = volatility(bars, index, 5);
        feature[10] = volatility(bars, index, 20);
        feature[11] = volatility(bars, index, 60);
        feature[12] = clamp(stockReturn - benchmarkReturn);
        feature[13] = marketSeries.periodReturns20().getOrDefault(current.tradeDate(), 0D);
        feature[14] = marketBreadth.getOrDefault(current.tradeDate(), 0D);
        if (!featureMetadata.isNewsFeaturesEnabled()) {
            return feature;
        }
        double[] newsFeatures = newsFeatureSnapshots == null ? null
                : newsFeatureSnapshots.get(key(stockCode, current.tradeDate()));
        if (newsFeatures == null || newsFeatures.length != featureMetadata.newsFeatureCount()) {
            throw new IllegalStateException("缺少 " + featureMetadata.getNewsFeatureVersion() + " 快照: "
                    + key(stockCode, current.tradeDate()));
        }
        System.arraycopy(newsFeatures, 0, feature, featureMetadata.marketFeatureCount(),
                featureMetadata.newsFeatureCount());
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

    private List<LstmDataPreprocessor.WalkForwardFold> buildWalkForwardBoundaries(
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
            folds.add(LstmDataPreprocessor.WalkForwardFold.builder()
                    .foldNumber(index + 1)
                    .strictIsolation(strictIsolation)
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

    private LstmDataPreprocessor.WalkForwardFold materializeFold(
            List<LstmDataPreprocessor.TrainingSample> allSamples,
            LstmDataPreprocessor.WalkForwardFold boundary,
            int continuousFeatureCount) {
        List<LstmDataPreprocessor.TrainingSample> train = copySamples(filterSamples(
                allSamples, boundary.getTrainStartDate(), boundary.getTrainEndDate()));
        List<LstmDataPreprocessor.TrainingSample> validation = copySamples(filterSamples(
                allSamples, boundary.getValidationStartDate(), boundary.getValidationEndDate()));
        List<LstmDataPreprocessor.TrainingSample> test = copySamples(filterSamples(
                allSamples, boundary.getTestStartDate(), boundary.getTestEndDate()));
        if (train.isEmpty() || validation.isEmpty() || test.isEmpty()) {
            throw new IllegalStateException("训练快照折叠边界无法匹配完整样本: fold="
                    + boundary.getFoldNumber());
        }
        NormalizationStats normalization = normalizeByTrainingWindow(
                train, validation, test, continuousFeatureCount);
        return LstmDataPreprocessor.WalkForwardFold.builder()
                .foldNumber(boundary.getFoldNumber())
                .strictIsolation(boundary.isStrictIsolation())
                .trainSamples(train)
                .valSamples(validation)
                .testSamples(test)
                .featureMeans(normalization.means())
                .featureStdDeviations(normalization.standardDeviations())
                .trainStartDate(boundary.getTrainStartDate())
                .trainEndDate(boundary.getTrainEndDate())
                .validationStartDate(boundary.getValidationStartDate())
                .validationEndDate(boundary.getValidationEndDate())
                .testStartDate(boundary.getTestStartDate())
                .testEndDate(boundary.getTestEndDate())
                .build();
    }

    private void validateRawSamples(
            LstmDataPreprocessor.ProcessedData rawData,
            ModelFeatureMetadata featureMetadata) {
        if (rawData.getFeatureCount() != featureMetadata.inputSize()
                || rawData.getSequenceLength() != config.getSequenceLength()) {
            throw new IllegalStateException("训练快照特征维度或序列长度与当前契约不一致");
        }
        for (LstmDataPreprocessor.TrainingSample sample : rawData.getAllSamples()) {
            if (sample.getInput() == null || sample.getInput().length != rawData.getSequenceLength()) {
                throw new IllegalStateException("训练快照样本序列长度不一致");
            }
            for (float[] row : sample.getInput()) {
                if (row == null || row.length != rawData.getFeatureCount()) {
                    throw new IllegalStateException("训练快照样本特征维度不一致");
                }
            }
        }
    }

    private void validateFoldBoundary(LstmDataPreprocessor.WalkForwardFold boundary) {
        if (boundary == null
                || boundary.getTrainStartDate() == null || boundary.getTrainEndDate() == null
                || boundary.getValidationStartDate() == null || boundary.getValidationEndDate() == null
                || boundary.getTestStartDate() == null || boundary.getTestEndDate() == null) {
            throw new IllegalStateException("训练快照包含不完整的 Walk-Forward 日期边界");
        }
        if (boundary.getTrainStartDate().isAfter(boundary.getTrainEndDate())
                || boundary.getValidationStartDate().isAfter(boundary.getValidationEndDate())
                || boundary.getTestStartDate().isAfter(boundary.getTestEndDate())
                || !boundary.getTrainEndDate().isBefore(boundary.getValidationStartDate())
                || !boundary.getValidationEndDate().isBefore(boundary.getTestStartDate())) {
            throw new IllegalStateException("训练快照包含倒置的 Walk-Forward 日期边界: fold="
                    + boundary.getFoldNumber());
        }
    }

    private List<LstmDataPreprocessor.TrainingSample> filterSamples(
            List<LstmDataPreprocessor.TrainingSample> samples, LocalDate startDate, LocalDate endDate) {
        return samples.stream()
                .filter(sample -> sample.getTargetDate() != null
                        && !sample.getTargetDate().isBefore(startDate)
                        && !sample.getTargetDate().isAfter(endDate))
                .toList();
    }

    private long countSamples(
            List<LstmDataPreprocessor.TrainingSample> samples, LocalDate startDate, LocalDate endDate) {
        return samples.stream()
                .filter(sample -> sample.getTargetDate() != null
                        && !sample.getTargetDate().isBefore(startDate)
                        && !sample.getTargetDate().isAfter(endDate))
                .count();
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
        LocalDate actualTrainStart = null;
        int trainCount = 0;
        int validationCount = 0;
        int testCount = 0;
        for (LstmDataPreprocessor.TrainingSample sample : samples) {
            LocalDate date = sample.getTargetDate();
            if (!date.isBefore(trainStart) && !date.isAfter(trainEnd)) {
                if (actualTrainStart == null || date.isBefore(actualTrainStart)) {
                    actualTrainStart = date;
                }
                trainCount++;
            } else if (!date.isBefore(validationStart) && !date.isAfter(validationEnd)) {
                validationCount++;
            } else if (!date.isBefore(testStart) && !date.isAfter(datasetEnd)) {
                testCount++;
            }
        }
        if (trainCount == 0 || validationCount == 0 || testCount == 0) {
            return null;
        }
        return new SplitResult(actualTrainStart, trainEnd,
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
            List<LstmDataPreprocessor.TrainingSample> test,
            int continuousFeatureCount) {
        double[] means = new double[continuousFeatureCount];
        double[] standardDeviations = new double[continuousFeatureCount];
        long observations = 0L;
        for (LstmDataPreprocessor.TrainingSample sample : train) {
            for (float[] row : sample.getInput()) {
                for (int index = 0; index < continuousFeatureCount; index++) {
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
                for (int index = 0; index < continuousFeatureCount; index++) {
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
        normalize(train, means, standardDeviations, continuousFeatureCount);
        normalize(validation, means, standardDeviations, continuousFeatureCount);
        normalize(test, means, standardDeviations, continuousFeatureCount);
        return new NormalizationStats(means, standardDeviations);
    }

    private void normalize(
            List<LstmDataPreprocessor.TrainingSample> samples, double[] means, double[] deviations,
            int continuousFeatureCount) {
        for (LstmDataPreprocessor.TrainingSample sample : samples) {
            for (float[] row : sample.getInput()) {
                for (int index = 0; index < continuousFeatureCount; index++) {
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

    private void validateConfiguration(ModelFeatureMetadata featureMetadata) {
        if (featureMetadata == null) {
            throw new IllegalStateException("模型特征元数据不能为空");
        }
        featureMetadata.validate();
        if (featureMetadata.marketFeatureCount() != 15
                || featureMetadata.categoryFeatureCount() != 3
                || featureMetadata.isNewsFeaturesEnabled()
                && featureMetadata.newsFeatureCount() != NewsFeatureSnapshotService.FEATURE_COUNT) {
            throw new IllegalStateException("当前预处理器不支持该模型特征元数据");
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
        if (config.getMaxPanelSamples() <= 0 || config.getMaxSamplesPerStock() <= 0) {
            throw new IllegalStateException("V6 样本数量上限必须大于 0");
        }
    }

    private Map<String, Set<Integer>> selectSampleTargetIndices(
            Map<String, List<AdjustedBar>> adjustedSeries,
            MarketSeries marketSeries) {
        List<SampleCandidate> candidates = new ArrayList<>();
        int sequenceLength = config.getSequenceLength();
        int perStockLimit = config.getMaxSamplesPerStock();
        for (Map.Entry<String, List<AdjustedBar>> entry : adjustedSeries.entrySet()) {
            List<Integer> eligibleIndices = new ArrayList<>();
            List<AdjustedBar> bars = entry.getValue();
            for (int targetIndex = sequenceLength; targetIndex + 1 < bars.size(); targetIndex++) {
                AdjustedBar targetBar = bars.get(targetIndex);
                if (isDerivedTradable(targetBar, bars.get(targetIndex + 1))
                        && marketSeries.openToOpenReturns().containsKey(targetBar.tradeDate())) {
                    eligibleIndices.add(targetIndex);
                }
            }
            for (Integer targetIndex : evenlySampleIndices(eligibleIndices, perStockLimit)) {
                candidates.add(new SampleCandidate(
                        entry.getKey(), targetIndex, bars.get(targetIndex).tradeDate()));
            }
        }

        List<SampleCandidate> selected = capPanelCandidates(candidates, config.getMaxPanelSamples());
        Map<String, Set<Integer>> result = new HashMap<>();
        for (SampleCandidate candidate : selected) {
            result.computeIfAbsent(candidate.stockCode(), ignored -> new HashSet<>())
                    .add(candidate.targetIndex());
        }
        long selectedDates = selected.stream().map(SampleCandidate::targetDate).distinct().count();
        log.info("V6 样本限流完成: perStockCandidates={}, selected={}, targetDates={}, "
                        + "maxPanelSamples={}, maxSamplesPerStock={}",
                candidates.size(), selected.size(), selectedDates,
                config.getMaxPanelSamples(), config.getMaxSamplesPerStock());
        return result;
    }

    private LocalDate dateFromKey(String snapshotKey) {
        int separator = snapshotKey.lastIndexOf('|');
        if (separator < 0 || separator == snapshotKey.length() - 1) {
            throw new IllegalStateException("新闻快照键格式无效: " + snapshotKey);
        }
        return LocalDate.parse(snapshotKey.substring(separator + 1));
    }

    private List<Integer> evenlySampleIndices(List<Integer> source, int limit) {
        if (source.size() <= limit) {
            return source;
        }
        List<Integer> result = new ArrayList<>(limit);
        if (limit == 1) {
            result.add(source.get(source.size() / 2));
            return result;
        }
        for (int index = 0; index < limit; index++) {
            int sourceIndex = (int) Math.round(index * (source.size() - 1D) / (limit - 1D));
            result.add(source.get(sourceIndex));
        }
        return result;
    }

    private List<SampleCandidate> capPanelCandidates(
            List<SampleCandidate> candidates, int panelLimit) {
        if (candidates.size() <= panelLimit) {
            return candidates;
        }
        Map<LocalDate, List<SampleCandidate>> candidatesByDate = candidates.stream()
                .collect(Collectors.groupingBy(
                        SampleCandidate::targetDate,
                        java.util.TreeMap::new,
                        Collectors.toCollection(ArrayList::new)));
        candidatesByDate.forEach((date, dateCandidates) -> dateCandidates.sort(
                Comparator.comparingInt((SampleCandidate candidate) ->
                                stableCandidateOrder(candidate.stockCode(), date))
                        .thenComparing(SampleCandidate::stockCode)));

        List<List<SampleCandidate>> dateBuckets = new ArrayList<>(candidatesByDate.values());
        int[] cursors = new int[dateBuckets.size()];
        List<SampleCandidate> selected = new ArrayList<>(panelLimit);
        while (selected.size() < panelLimit) {
            boolean progressed = false;
            for (int bucketIndex = 0;
                 bucketIndex < dateBuckets.size() && selected.size() < panelLimit;
                 bucketIndex++) {
                List<SampleCandidate> bucket = dateBuckets.get(bucketIndex);
                if (cursors[bucketIndex] < bucket.size()) {
                    selected.add(bucket.get(cursors[bucketIndex]++));
                    progressed = true;
                }
            }
            if (!progressed) {
                break;
            }
        }
        return selected;
    }

    private int stableCandidateOrder(String stockCode, LocalDate targetDate) {
        int hash = 31 * stockCode.hashCode() + targetDate.hashCode();
        return hash ^ (hash >>> 16);
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
            Map<LocalDate, Double> openToOpenReturns,
            Map<LocalDate, Double> periodReturns20) {
    }

    private record SampleCandidate(String stockCode, int targetIndex, LocalDate targetDate) {
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
// AI_GENERATE_END-------------
