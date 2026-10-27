// AI_GENERATE_START ------
package com.stock.model.service;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 日级新闻时序特征快照服务。
 * 负责生成、持久化和完整批量读取 `temporal-news-v1`，缺失快照时阻断模型训练或推理。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NewsFeatureSnapshotService {

    /** 新闻时序特征版本。 */
    public static final String FEATURE_VERSION = "temporal-news-v1";

    /** 新闻连续特征数量。 */
    public static final int FEATURE_COUNT = 25;

    private static final int QUERY_STOCK_BATCH_SIZE = 50;
    private static final int MAX_QUERY_ATTEMPTS = 3;
    private static final long QUERY_RETRY_DELAY_MILLIS = 1000L;

    private final NewsFeatureSnapshotRepository repository;
    private final NewsEventFeatureService featureService;

    /**
     * 生成并保存单只股票的日级新闻特征快照。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @return 已保存快照
     * @throws IllegalArgumentException 股票代码、交易日或截止时间无效时抛出
     * @throws IllegalStateException 特征维度、有限性或取值范围无效时抛出
     */
    public NewsFeatureSnapshotDocument generateSnapshot(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime) {
        return generateSnapshot(stockCode, featureTradeDate, cutoffTime, null, null);
    }

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
        NewsEventFeatureVector vector = featureService.buildFeature(stockCode, featureTradeDate, cutoffTime);
        if (!validFeatures(vector.getTemporalFeatures())) {
            throw new IllegalStateException("新闻特征必须满足 " + FEATURE_COUNT + " 维数值契约");
        }
        NewsFeatureSnapshotDocument document = repository
                .findByFeatureVersionAndStockCodeAndFeatureTradeDate(
                        FEATURE_VERSION, stockCode.trim(), featureTradeDate)
                .orElseGet(NewsFeatureSnapshotDocument::new);
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
        return repository.save(document);
    }

    /**
     * 批量读取完整快照，并校验调用方要求的股票交易日键全部存在。
     *
     * @param stockCodes 股票代码集合
     * @param startDate 起始交易日
     * @param endDate 结束交易日
     * @param requiredKeys 必须存在的 `股票代码|交易日` 集合
     * @return 快照键到 25 维特征的映射
     * @throws IllegalStateException 快照缺失、截止时间错误或特征数值无效时抛出
     */
    public Map<String, double[]> loadRequired(
            Collection<String> stockCodes,
            LocalDate startDate,
            LocalDate endDate,
            Set<String> requiredKeys) {
        Map<String, double[]> result = new LinkedHashMap<>();
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
                if (!document.isComplete() || document.getFeatures() == null
                        || document.getFeatures().size() != FEATURE_COUNT
                        || document.getCutoffTime() == null
                        || document.getFeatureTradeDate() == null
                        || !document.getCutoffTime().toLocalDate().equals(document.getFeatureTradeDate())
                        || (document.getLatestInputPublishTime() != null
                        && document.getLatestInputPublishTime().isAfter(document.getCutoffTime()))
                        || !validFeatures(document.getFeatures())) {
                    continue;
                }
                result.put(key(document.getStockCode(), document.getFeatureTradeDate()),
                        document.getFeatures().stream().mapToDouble(Double::doubleValue).toArray());
            }
        }
        List<String> missing = requiredKeys.stream().filter(key -> !result.containsKey(key)).limit(20).toList();
        if (!missing.isEmpty() || result.size() < requiredKeys.size()) {
            throw new IllegalStateException("temporal-news-v1 快照不完整，禁止训练或推理，缺失示例: " + missing);
        }
        return result;
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
            double minimum = index == 3 ? -1D : 0D;
            if (value < minimum || value > 1D) {
                return false;
            }
        }
        return true;
    }

    private String key(String stockCode, LocalDate tradeDate) {
        return stockCode + "|" + tradeDate;
    }
}
// AI_GENERATE_END ------
