// AI_GENERATE_START----------------
package com.stock.model.service;

import com.stock.data.dto.ModelStockDailyBarDto;
import com.stock.data.service.MarketDataQueryService;
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelFeatureMetadata;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * V6 Ranking LSTM 训练数据加载服务。
 * 只加载 Tushare 免费模型日线和点时新闻快照，不依赖付费复权、历史行业或精确可交易事实。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingLstmTrainingDataService {

    private final LstmTrainingConfig config;
    private final LstmDataQualityConfig dataQualityConfig;
    private final MarketDataQueryService marketDataQueryService;
    private final NewsFeatureSnapshotService newsFeatureSnapshotService;

    /**
     * 加载一次训练快照需要的 V6 数据。
     *
     * @param stockCodes 参与训练的股票代码
     * @param days 每只股票读取的最大交易日数量
     * @param featureMetadata 本次训练的特征元数据
     * @return 已完成完整性门禁的数据集合
     */
    public RankingTrainingDataBundle loadTrainingData(
            List<String> stockCodes, int days, ModelFeatureMetadata featureMetadata) {
        return loadData(stockCodes, days, null, featureMetadata);
    }

    /**
     * 加载指定历史截止日的 V6 推理数据。
     *
     * @param stockCodes 股票代码
     * @param days 每只股票最大交易日数量
     * @param endDate 行情截止日期，包含当天
     * @param featureMetadata 模型制品中固化的特征元数据
     * @return 版本化推理数据集合
     */
    public RankingTrainingDataBundle loadPredictionData(
            List<String> stockCodes, int days, LocalDate endDate,
            ModelFeatureMetadata featureMetadata) {
        return loadData(stockCodes, days, endDate, featureMetadata);
    }

    private RankingTrainingDataBundle loadData(
            List<String> stockCodes, int days, LocalDate endDate,
            ModelFeatureMetadata featureMetadata) {
        featureMetadata.validate();
        String source = requireText(config.getDataSource(), "模型数据来源不能为空");
        String sourceVersion = requireText(config.getDataSourceVersion(), "模型数据版本不能为空");
        String benchmarkCode = requireText(config.getBenchmarkSeriesCode(), "市场基准代码不能为空");
        List<String> codes = normalizedCodes(stockCodes);
        if (codes.isEmpty()) {
            throw new IllegalStateException("V6 训练股票范围为空");
        }

        int queryLimit = Math.max(days, config.getSequenceLength() + 2);
        Map<String, List<ModelStockDailyBarDto>> priceSeries = new LinkedHashMap<>();
        Map<String, List<ModelStockDailyBarDto>> loadedSeries = marketDataQueryService
                .findModelStockBarsByStockCodes(codes, endDate, source, sourceVersion, queryLimit);
        for (String code : codes) {
            List<ModelStockDailyBarDto> bars = new ArrayList<>(
                    loadedSeries.getOrDefault(code, List.of()));
            if (!bars.isEmpty()) {
                Collections.reverse(bars);
                priceSeries.put(code, bars);
            }
        }
        if (priceSeries.isEmpty()) {
            throw new IllegalStateException("model_stock_daily_bars 没有匹配 V6 来源版本的训练行情");
        }
        requireKeyCoverage("model_stock_daily_bars", codes, priceSeries.keySet());

        LocalDate startDate = priceSeries.values().stream().flatMap(List::stream)
                .map(ModelStockDailyBarDto::getTradeDate).min(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalStateException("V6 模型日线缺少开始日期"));
        LocalDate effectiveEndDate = priceSeries.values().stream().flatMap(List::stream)
                .map(ModelStockDailyBarDto::getTradeDate).max(LocalDate::compareTo)
                .orElseThrow(() -> new IllegalStateException("V6 模型日线缺少结束日期"));
        Set<String> requiredNewsSnapshotKeys = new LinkedHashSet<>();
        Map<String, double[]> newsFeatureSnapshots = Map.of();
        if (featureMetadata.isNewsFeaturesEnabled()) {
            priceSeries.forEach((stockCode, bars) -> bars.forEach(bar ->
                    requiredNewsSnapshotKeys.add(stockCode + "|" + bar.getTradeDate())));
            newsFeatureSnapshots = newsFeatureSnapshotService.loadRequired(
                    priceSeries.keySet(), startDate, effectiveEndDate, requiredNewsSnapshotKeys);
        }

        long barCount = priceSeries.values().stream().mapToLong(List::size).sum();
        log.info("V6 训练数据加载完成: requestedStocks={}, loadedStocks={}, bars={}, newsSnapshots={}, "
                        + "source={}, sourceVersion={}, benchmark={}",
                codes.size(), priceSeries.size(), barCount,
                newsFeatureSnapshots.size(), source, sourceVersion, benchmarkCode);
        return RankingTrainingDataBundle.builder()
                .priceSeries(priceSeries)
                .source(source)
                .sourceVersion(sourceVersion)
                .benchmarkCode(benchmarkCode)
                .featureMetadata(featureMetadata)
                .newsFeatureSnapshots(newsFeatureSnapshots)
                .build();
    }

    private List<String> normalizedCodes(Collection<String> stockCodes) {
        return stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private void requireKeyCoverage(
            String sourceName, Collection<String> requiredCodes, Collection<String> actualCodes) {
        Set<String> required = requiredCodes.stream()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actual = actualCodes.stream()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        double minimumRate = dataQualityConfig.getMinimumSourceCoverageRate();
        if (!Double.isFinite(minimumRate) || minimumRate <= 0D || minimumRate > 1D) {
            throw new IllegalStateException("关键数据源最低覆盖率配置必须在 0 到 1 之间");
        }
        long covered = required.stream().filter(actual::contains).count();
        double coverageRate = required.isEmpty() ? 0D : covered / (double) required.size();
        if (coverageRate < minimumRate) {
            List<String> missing = required.stream().filter(code -> !actual.contains(code)).limit(20).toList();
            throw new IllegalStateException(sourceName + " 标识覆盖率不足: required=" + required.size()
                    + ", covered=" + covered + ", rate=" + coverageRate + ", missing=" + missing);
        }
    }

    /**
     * V6 训练数据集合。
     *
     * @author mwangli
     * @since 2026-10-23
     */
    @Data
    @Builder
    public static class RankingTrainingDataBundle {
        /** 股票代码到模型专用日线的映射。 */
        private Map<String, List<ModelStockDailyBarDto>> priceSeries;
        /** 数据来源。 */
        private String source;
        /** 数据版本。 */
        private String sourceVersion;
        /** 训练股票池等权市场基准代码。 */
        private String benchmarkCode;
        /** 本次训练或推理使用的不可变特征元数据。 */
        private ModelFeatureMetadata featureMetadata;
        /** `股票代码|交易日` 到版本化紧凑新闻向量的映射。 */
        private Map<String, double[]> newsFeatureSnapshots;
    }
}
// AI_GENERATE_END----------------
