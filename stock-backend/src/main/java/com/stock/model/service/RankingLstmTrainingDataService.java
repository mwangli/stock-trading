// AI_GENERATE_START --
package com.stock.model.service;

import com.stock.data.entity.HistoricalStockUniverseEntity;
import com.stock.data.entity.ModelReferenceDailyBar;
import com.stock.data.entity.ModelStockDailyBar;
import com.stock.data.entity.ReferenceSeriesType;
import com.stock.data.entity.StockAdjustmentFactor;
import com.stock.data.entity.StockDailyTradabilityFact;
import com.stock.data.repository.HistoricalStockUniverseRepository;
import com.stock.data.repository.ModelReferenceDailyBarRepository;
import com.stock.data.repository.ModelStockDailyBarRepository;
import com.stock.data.repository.StockAdjustmentFactorRepository;
import com.stock.data.repository.StockDailyTradabilityFactRepository;
import com.stock.model.config.LstmTrainingConfig;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * `v5` Ranking LSTM 训练数据加载服务。
 * 只读取固定来源版本的模型日线、复权、参考序列、历史股票池和可交易事实，不回退到 Legacy 行情。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingLstmTrainingDataService {

    private final LstmTrainingConfig config;
    private final ModelStockDailyBarRepository stockDailyBarRepository;
    private final ModelReferenceDailyBarRepository referenceDailyBarRepository;
    private final StockAdjustmentFactorRepository adjustmentFactorRepository;
    private final HistoricalStockUniverseRepository historicalStockUniverseRepository;
    private final StockDailyTradabilityFactRepository tradabilityFactRepository;

    /**
     * 加载一次训练快照需要的版本化数据。
     *
     * @param stockCodes 参与训练的股票代码
     * @param days 每只股票读取的最大交易日数量
     * @return 已完成完整性门禁的数据集合
     */
    public RankingTrainingDataBundle loadTrainingData(List<String> stockCodes, int days) {
        return loadData(stockCodes, days, null);
    }

    /**
     * 加载指定历史截止日的 `v5` 推理数据。
     *
     * @param stockCodes 股票代码
     * @param days 每只股票最大交易日数量
     * @param endDate 行情截止日期，包含当天
     * @return 版本化推理数据集合
     */
    public RankingTrainingDataBundle loadPredictionData(
            List<String> stockCodes, int days, LocalDate endDate) {
        return loadData(stockCodes, days, endDate);
    }

    private RankingTrainingDataBundle loadData(
            List<String> stockCodes, int days, LocalDate endDate) {
        String source = requireText(config.getDataSource(), "模型数据来源不能为空");
        String sourceVersion = requireText(config.getDataSourceVersion(), "模型数据版本不能为空");
        String benchmarkCode = requireText(config.getBenchmarkSeriesCode(), "市场基准序列代码不能为空");
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (codes.isEmpty()) {
            throw new IllegalStateException("v5 训练股票范围为空");
        }

        int queryLimit = Math.max(days, config.getSequenceLength() + 2);
        PageRequest pageRequest = PageRequest.of(0, queryLimit);
        Map<String, List<ModelStockDailyBar>> priceSeries = new LinkedHashMap<>();
        for (String code : codes) {
            List<ModelStockDailyBar> bars = new ArrayList<>(endDate == null
                    ? stockDailyBarRepository.findByStockCodeAndSourceAndSourceVersionOrderByTradeDateDesc(
                            code, source, sourceVersion, pageRequest)
                    : stockDailyBarRepository
                            .findByStockCodeAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByTradeDateDesc(
                                    code, endDate, source, sourceVersion, pageRequest));
            if (!bars.isEmpty()) {
                Collections.reverse(bars);
                priceSeries.put(code, bars);
            }
        }
        if (priceSeries.isEmpty()) {
            throw new IllegalStateException("model_stock_daily_bars 没有匹配来源版本的训练行情");
        }

        List<StockAdjustmentFactor> adjustmentFactors = endDate == null
                ? adjustmentFactorRepository
                .findByStockCodeInAndSourceAndSourceVersionOrderByStockCodeAscTradeDateAsc(
                        priceSeries.keySet(), source, sourceVersion)
                : adjustmentFactorRepository
                .findByStockCodeInAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByStockCodeAscTradeDateAsc(
                        priceSeries.keySet(), endDate, source, sourceVersion);
        if (adjustmentFactors.isEmpty()) {
            throw new IllegalStateException("stock_adjustment_factors 缺少匹配来源版本的复权因子");
        }

        List<HistoricalStockUniverseEntity> universe = endDate == null
                ? historicalStockUniverseRepository
                .findByStockCodeInAndSourceAndSourceVersionOrderByStockCodeAscEffectiveFromAsc(
                        priceSeries.keySet(), source, sourceVersion)
                : historicalStockUniverseRepository
                .findByStockCodeInAndEffectiveFromLessThanEqualAndSourceAndSourceVersionOrderByStockCodeAscEffectiveFromAsc(
                        priceSeries.keySet(), endDate, source, sourceVersion);
        if (universe.isEmpty()) {
            throw new IllegalStateException("historical_stock_universe 缺少匹配来源版本的历史股票池");
        }

        List<ModelReferenceDailyBar> benchmarkBars = loadReferenceBars(
                ReferenceSeriesType.MARKET, benchmarkCode, source, sourceVersion, endDate);
        if (benchmarkBars.isEmpty()) {
            throw new IllegalStateException("model_reference_daily_bars 缺少市场基准序列: " + benchmarkCode);
        }

        Map<String, List<ModelReferenceDailyBar>> industryReferenceBars = new LinkedHashMap<>();
        Set<String> industryCodes = new LinkedHashSet<>();
        for (HistoricalStockUniverseEntity item : universe) {
            if (item.getIndustryCode() != null && !item.getIndustryCode().isBlank()) {
                industryCodes.add(item.getIndustryCode().trim());
            }
        }
        for (String industryCode : industryCodes) {
            List<ModelReferenceDailyBar> bars = loadReferenceBars(
                    ReferenceSeriesType.INDUSTRY, industryCode, source, sourceVersion, endDate);
            if (!bars.isEmpty()) {
                industryReferenceBars.put(industryCode, bars);
            }
        }

        LocalDate startDate = priceSeries.values().stream().flatMap(List::stream)
                .map(ModelStockDailyBar::getTradeDate).min(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalStateException("模型日线缺少开始日期"));
        LocalDate effectiveEndDate = priceSeries.values().stream().flatMap(List::stream)
                .map(ModelStockDailyBar::getTradeDate).max(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalStateException("模型日线缺少结束日期"));
        List<StockDailyTradabilityFact> tradabilityFacts = tradabilityFactRepository
                .findByStockCodeInAndTradeDateBetweenAndSourceAndSourceVersion(
                        priceSeries.keySet(), startDate, effectiveEndDate, source, sourceVersion);
        if (tradabilityFacts.isEmpty()) {
            throw new IllegalStateException("stock_daily_tradability_facts 缺少训练区间可交易事实");
        }

        log.info("v5 训练数据加载完成: requestedStocks={}, loadedStocks={}, factors={}, universe={}, "
                        + "tradability={}, benchmarkBars={}, industrySeries={}, source={}, sourceVersion={}",
                codes.size(), priceSeries.size(), adjustmentFactors.size(), universe.size(),
                tradabilityFacts.size(), benchmarkBars.size(), industryReferenceBars.size(), source, sourceVersion);
        return RankingTrainingDataBundle.builder()
                .priceSeries(priceSeries)
                .adjustmentFactors(adjustmentFactors)
                .historicalUniverse(universe)
                .tradabilityFacts(tradabilityFacts)
                .benchmarkBars(benchmarkBars)
                .industryReferenceBars(industryReferenceBars)
                .source(source)
                .sourceVersion(sourceVersion)
                .benchmarkCode(benchmarkCode)
                .build();
    }

    private List<ModelReferenceDailyBar> loadReferenceBars(
            ReferenceSeriesType seriesType,
            String seriesCode,
            String source,
            String sourceVersion,
            LocalDate endDate) {
        return endDate == null
                ? referenceDailyBarRepository
                .findBySeriesTypeAndSeriesCodeAndSourceAndSourceVersionOrderByTradeDateAsc(
                        seriesType, seriesCode, source, sourceVersion)
                : referenceDailyBarRepository
                .findBySeriesTypeAndSeriesCodeAndTradeDateLessThanEqualAndSourceAndSourceVersionOrderByTradeDateAsc(
                        seriesType, seriesCode, endDate, source, sourceVersion);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    /**
     * `v5` 训练数据集合。
     *
     * @author mwangli
     * @since 2026-10-23
     */
    @Data
    @Builder
    public static class RankingTrainingDataBundle {
        /** 股票代码到模型专用日线的映射。 */
        private Map<String, List<ModelStockDailyBar>> priceSeries;
        /** 股票复权因子。 */
        private List<StockAdjustmentFactor> adjustmentFactors;
        /** 历史股票池记录。 */
        private List<HistoricalStockUniverseEntity> historicalUniverse;
        /** 股票单日可交易事实。 */
        private List<StockDailyTradabilityFact> tradabilityFacts;
        /** 市场基准日线。 */
        private List<ModelReferenceDailyBar> benchmarkBars;
        /** 行业代码到行业参考日线的映射。 */
        private Map<String, List<ModelReferenceDailyBar>> industryReferenceBars;
        /** 数据来源。 */
        private String source;
        /** 数据版本。 */
        private String sourceVersion;
        /** 市场基准代码。 */
        private String benchmarkCode;
    }
}
// AI_GENERATE_END --
