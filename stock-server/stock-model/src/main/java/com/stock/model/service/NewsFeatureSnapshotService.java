// AI_GENERATE_START------------------
package com.stock.model.service;

import com.stock.data.dto.MarketNewsEventDto;
import com.stock.model.dto.NewsEventFeatureVector;
import com.stock.model.entity.NewsFeatureSnapshotDocument;
import com.stock.model.repository.NewsFeatureSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日级新闻时序特征快照服务。
 * 负责生成有事件稀疏快照，并基于 COMPLETE 日期水位批量推导 NO_EVENT。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NewsFeatureSnapshotService {

    /** 新闻时序特征版本。 */
    public static final String FEATURE_VERSION = "temporal-news-v2-8d";

    /** 新闻连续特征数量。 */
    public static final int FEATURE_COUNT = 8;

    private static final int QUERY_STOCK_BATCH_SIZE = 50;
    private static final int MAX_QUERY_ATTEMPTS = 3;
    private static final long QUERY_RETRY_DELAY_MILLIS = 1000L;

    private final NewsFeatureSnapshotRepository repository;
    private final NewsEventFeatureService featureService;
    private final NewsFeatureCompletenessService completenessService;

    /**
     * 生成带模型行情来源追踪信息的日级新闻特征快照。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @return 已保存快照
     * @throws IllegalArgumentException 股票代码、交易日或截止时间无效时抛出
     * @throws IllegalStateException 特征维度、有限性或点时信息无效时抛出
     */
    public NewsFeatureSnapshotDocument generateSnapshot(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            String marketDataSource, String marketDataSourceVersion) {
        validateSnapshotIdentity(stockCode, featureTradeDate, cutoffTime);
        if (!hasText(marketDataSource) || !hasText(marketDataSourceVersion)) {
            throw new IllegalArgumentException("新闻特征快照必须提供行情来源和来源版本");
        }
        NewsEventFeatureVector vector = featureService.buildFeature(stockCode, featureTradeDate, cutoffTime);
        return saveSnapshot(stockCode, featureTradeDate, cutoffTime,
                marketDataSource, marketDataSourceVersion, vector);
    }

    /**
     * 使用已批量加载的事件生成日级新闻特征快照。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @param tradeSignals 已按点时窗口加载的交易信号事件
     * @return 已保存快照
     * @throws IllegalArgumentException 股票代码、交易日、截止时间或来源无效时抛出
     * @throws IllegalStateException 特征维度、有限性或点时信息无效时抛出
     */
    public NewsFeatureSnapshotDocument generateSnapshot(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            String marketDataSource, String marketDataSourceVersion,
            List<MarketNewsEventDto> tradeSignals) {
        return generateSnapshot(stockCode, featureTradeDate, cutoffTime,
                marketDataSource, marketDataSourceVersion, tradeSignals, null);
    }

    /**
     * 使用已批量加载事件和情感结果生成日级新闻特征快照。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @param tradeSignals 已按点时窗口加载的交易信号事件
     * @param sentimentModelVersion 已批量预加载的情感模型版本
     * @return 已保存快照
     * @throws IllegalArgumentException 股票代码、交易日、截止时间或来源无效时抛出
     * @throws IllegalStateException 特征维度、有限性或点时信息无效时抛出
     */
    public NewsFeatureSnapshotDocument generateSnapshot(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            String marketDataSource, String marketDataSourceVersion,
            List<MarketNewsEventDto> tradeSignals, String sentimentModelVersion) {
        validateSnapshotIdentity(stockCode, featureTradeDate, cutoffTime);
        if (!hasText(marketDataSource) || !hasText(marketDataSourceVersion)) {
            throw new IllegalArgumentException("新闻特征快照必须提供行情来源和来源版本");
        }
        NewsFeatureSnapshotDocument existingDocument = repository
                .findByFeatureVersionAndStockCodeAndFeatureTradeDate(
                        FEATURE_VERSION, stockCode.trim(), featureTradeDate)
                .orElse(null);
        return repository.save(prepareSnapshot(
                stockCode, featureTradeDate, cutoffTime, marketDataSource, marketDataSourceVersion,
                tradeSignals, sentimentModelVersion, existingDocument));
    }

    /**
     * 使用已批量加载的事件和已有文档构建快照，但不立即写入 MongoDB。
     * 历史回填可先构建同一日期的全部快照，再统一批量保存。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @param tradeSignals 已按点时窗口加载的交易信号事件
     * @param sentimentModelVersion 已批量预加载的情感模型版本
     * @param existingDocument 已批量加载的已有快照；不存在时为空
     * @return 完成契约校验但尚未持久化的快照文档
     * @throws IllegalArgumentException 股票代码、交易日、截止时间或来源无效时抛出
     * @throws IllegalStateException 特征维度、有限性或点时信息无效时抛出
     */
    public NewsFeatureSnapshotDocument prepareSnapshot(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            String marketDataSource, String marketDataSourceVersion,
            List<MarketNewsEventDto> tradeSignals, String sentimentModelVersion,
            NewsFeatureSnapshotDocument existingDocument) {
        validateSnapshotIdentity(stockCode, featureTradeDate, cutoffTime);
        if (!hasText(marketDataSource) || !hasText(marketDataSourceVersion)) {
            throw new IllegalArgumentException("新闻特征快照必须提供行情来源和来源版本");
        }
        NewsEventFeatureVector vector = featureService.buildFeature(
                stockCode, featureTradeDate, cutoffTime, tradeSignals, sentimentModelVersion);
        return prepareSnapshotDocument(stockCode, featureTradeDate, cutoffTime,
                marketDataSource, marketDataSourceVersion, vector, existingDocument);
    }

    private NewsFeatureSnapshotDocument saveSnapshot(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            String marketDataSource, String marketDataSourceVersion,
            NewsEventFeatureVector vector) {
        if (!validFeatures(vector.getTemporalFeatures())) {
            throw new IllegalStateException("新闻特征必须满足 " + FEATURE_COUNT + " 维数值契约");
        }
        if (vector.isNoEffectiveEvent() || vector.getInputEventCount() <= 0) {
            throw new IllegalStateException("稀疏新闻特征快照必须至少包含一个有效事件");
        }
        if (!hasText(vector.getPipelineVersion()) || !hasText(vector.getSentimentModelVersion())) {
            throw new IllegalStateException("有事件新闻特征快照缺少管线或情感模型版本");
        }
        NewsFeatureSnapshotDocument existingDocument = repository
                .findByFeatureVersionAndStockCodeAndFeatureTradeDate(
                        FEATURE_VERSION, stockCode.trim(), featureTradeDate)
                .orElse(null);
        return repository.save(prepareSnapshotDocument(
                stockCode, featureTradeDate, cutoffTime,
                marketDataSource, marketDataSourceVersion, vector, existingDocument));
    }

    private NewsFeatureSnapshotDocument prepareSnapshotDocument(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime,
            String marketDataSource, String marketDataSourceVersion,
            NewsEventFeatureVector vector, NewsFeatureSnapshotDocument existingDocument) {
        if (!validFeatures(vector.getTemporalFeatures())) {
            throw new IllegalStateException("新闻特征必须满足 " + FEATURE_COUNT + " 维数值契约");
        }
        if (vector.isNoEffectiveEvent() || vector.getInputEventCount() <= 0) {
            throw new IllegalStateException("稀疏新闻特征快照必须至少包含一个有效事件");
        }
        if (!hasText(vector.getPipelineVersion()) || !hasText(vector.getSentimentModelVersion())) {
            throw new IllegalStateException("有事件新闻特征快照缺少管线或情感模型版本");
        }
        NewsFeatureSnapshotDocument document = existingDocument == null
                ? new NewsFeatureSnapshotDocument() : existingDocument;
        document.setStockCode(stockCode.trim());
        document.setFeatureTradeDate(featureTradeDate);
        document.setCutoffTime(cutoffTime);
        document.setFeatureVersion(FEATURE_VERSION);
        document.setFeatures(List.copyOf(vector.getTemporalFeatures()));
        document.setSentimentModelVersion(vector.getSentimentModelVersion());
        document.setPipelineVersion(vector.getPipelineVersion());
        document.setMarketDataSource(marketDataSource);
        document.setMarketDataSourceVersion(marketDataSourceVersion);
        document.setInputEventCount(vector.getInputEventCount());
        document.setLatestInputPublishTime(vector.getLatestInputPublishTime());
        if (vector.getLatestInputPublishTime() != null
                && vector.getLatestInputPublishTime().isAfter(cutoffTime)) {
            throw new IllegalStateException("新闻特征快照包含截止时间之后的事件");
        }
        document.setComplete(true);
        document.setGeneratedAt(LocalDateTime.now());
        return document;
    }

    /**
     * 批量读取完整快照，并校验调用方要求的股票交易日键全部存在。
     *
     * @param stockCodes 股票代码集合
     * @param startDate 起始交易日
     * @param endDate 结束交易日
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @param requiredKeys 必须可判定的 `股票代码|交易日` 集合
     * @return 快照键到紧凑新闻特征的映射
     * @throws IllegalStateException 快照缺失、截止时间错误或特征数值无效时抛出
     */
    public Map<String, double[]> loadRequired(
            Collection<String> stockCodes,
            LocalDate startDate,
            LocalDate endDate,
            String marketDataSource,
            String marketDataSourceVersion,
            Set<String> requiredKeys) {
        if (requiredKeys == null || requiredKeys.isEmpty()) {
            return Map.of();
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalStateException("新闻特征读取日期范围无效");
        }
        Set<String> normalizedRequiredKeys = new LinkedHashSet<>(requiredKeys);
        Map<String, double[]> result = new LinkedHashMap<>();
        Set<String> invalidKeys = new LinkedHashSet<>();
        List<String> codes = stockCodes == null ? List.of() : stockCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        for (int offset = 0; offset < codes.size(); offset += QUERY_STOCK_BATCH_SIZE) {
            List<String> batchCodes = new ArrayList<>(codes.subList(
                    offset, Math.min(offset + QUERY_STOCK_BATCH_SIZE, codes.size())));
            List<NewsFeatureSnapshotDocument> documents = loadBatchWithRetry(
                    batchCodes, startDate, endDate);
            for (NewsFeatureSnapshotDocument document : documents) {
                String snapshotKey = key(document.getStockCode(), document.getFeatureTradeDate());
                if (!normalizedRequiredKeys.contains(snapshotKey)) {
                    continue;
                }
                if (!isUsableSnapshot(document, marketDataSource, marketDataSourceVersion)) {
                    invalidKeys.add(snapshotKey);
                    continue;
                }
                result.put(snapshotKey,
                        document.getFeatures().stream().mapToDouble(Double::doubleValue).toArray());
            }
        }

        if (!invalidKeys.isEmpty()) {
            throw new IllegalStateException(FEATURE_VERSION
                    + " 存在契约、来源或点时边界无效的快照，禁止解释为 NO_EVENT，示例: "
                    + invalidKeys.stream().limit(20).toList());
        }

        Set<String> missingKeys = normalizedRequiredKeys.stream()
                .filter(snapshotKey -> !result.containsKey(snapshotKey))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<LocalDate> missingDates = missingKeys.stream()
                .map(this::dateFromKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<LocalDate> completeDates = completenessService.findCompleteDates(
                FEATURE_VERSION, marketDataSource, marketDataSourceVersion, missingDates);
        List<String> unavailable = missingKeys.stream()
                .filter(snapshotKey -> !completeDates.contains(dateFromKey(snapshotKey)))
                .limit(20)
                .toList();
        if (!unavailable.isEmpty()) {
            throw new IllegalStateException(FEATURE_VERSION
                    + " 日期完备性水位缺失，禁止将未知状态解释为 NO_EVENT，缺失示例: " + unavailable);
        }
        double[] noEventFeatures = new double[FEATURE_COUNT];
        missingKeys.forEach(snapshotKey -> result.put(snapshotKey, noEventFeatures.clone()));
        return result;
    }

    private LocalDate dateFromKey(String snapshotKey) {
        int separator = snapshotKey.lastIndexOf('|');
        if (separator < 0 || separator == snapshotKey.length() - 1) {
            throw new IllegalStateException("新闻快照键格式无效: " + snapshotKey);
        }
        return LocalDate.parse(snapshotKey.substring(separator + 1));
    }

    boolean isUsableSnapshot(
            NewsFeatureSnapshotDocument document,
            String marketDataSource,
            String marketDataSourceVersion) {
        return document != null
                && document.isComplete()
                && document.getFeatures() != null
                && document.getFeatures().size() == FEATURE_COUNT
                && document.getCutoffTime() != null
                && document.getFeatureTradeDate() != null
                && document.getCutoffTime().toLocalDate().equals(document.getFeatureTradeDate())
                && java.util.Objects.equals(marketDataSource, document.getMarketDataSource())
                && java.util.Objects.equals(marketDataSourceVersion, document.getMarketDataSourceVersion())
                && document.getInputEventCount() > 0
                && hasText(document.getPipelineVersion())
                && hasText(document.getSentimentModelVersion())
                && (document.getLatestInputPublishTime() == null
                || !document.getLatestInputPublishTime().isAfter(document.getCutoffTime()))
                && validFeatures(document.getFeatures());
    }

    private List<NewsFeatureSnapshotDocument> loadBatchWithRetry(
            List<String> stockCodes, LocalDate startDate, LocalDate endDate) {
        for (int attempt = 1; attempt <= MAX_QUERY_ATTEMPTS; attempt++) {
            try {
                return repository.findByFeatureVersionAndStockCodeInAndFeatureTradeDateBetween(
                        FEATURE_VERSION, stockCodes, startDate, endDate);
            } catch (DataAccessException exception) {
                if (attempt == MAX_QUERY_ATTEMPTS) {
                    throw exception;
                }
                log.warn("新闻特征快照批量读取失败，准备重试: attempt={}/{}, stocks={}, reason={}",
                        attempt, MAX_QUERY_ATTEMPTS, stockCodes.size(), exception.getMessage());
                sleepBeforeRetry();
            }
        }
        throw new IllegalStateException("新闻特征快照批量读取重试状态异常");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(QUERY_RETRY_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("新闻特征快照批量读取重试被中断", exception);
        }
    }

    private void validateSnapshotIdentity(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime) {
        if (stockCode == null || stockCode.isBlank()
                || featureTradeDate == null || cutoffTime == null) {
            throw new IllegalArgumentException("新闻特征快照必须提供股票代码、交易日和截止时间");
        }
        if (!cutoffTime.toLocalDate().equals(featureTradeDate)) {
            throw new IllegalArgumentException("新闻特征截止时间必须属于特征交易日");
        }
    }

    private boolean validFeatures(List<Double> features) {
        if (features == null || features.size() != FEATURE_COUNT) {
            return false;
        }
        for (int index = 0; index < features.size(); index++) {
            Double value = features.get(index);
            if (value == null || !Double.isFinite(value)) {
                return false;
            }
            double minimum = index == 0 ? -1D : 0D;
            if (value < minimum || value > 1D) {
                return false;
            }
        }
        return true;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String key(String stockCode, LocalDate tradeDate) {
        return stockCode + "|" + tradeDate;
    }
}
// AI_GENERATE_END------------------
