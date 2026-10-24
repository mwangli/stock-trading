// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.model.dto.NewsEventFeatureVector;
import com.stock.model.entity.NewsFeatureSnapshotDocument;
import com.stock.model.repository.NewsFeatureSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
@RequiredArgsConstructor
public class NewsFeatureSnapshotService {

    /** 新闻时序特征版本。 */
    public static final String FEATURE_VERSION = "temporal-news-v1";

    /** 新闻连续特征数量。 */
    public static final int FEATURE_COUNT = 25;

    private final NewsFeatureSnapshotRepository repository;
    private final NewsEventFeatureService featureService;

    /**
     * 生成并保存单只股票的日级新闻特征快照。
     *
     * @param stockCode 股票代码
     * @param featureTradeDate 特征归属交易日
     * @param cutoffTime 信息截止时间
     * @return 已保存快照
     */
    public NewsFeatureSnapshotDocument generateSnapshot(
            String stockCode, LocalDate featureTradeDate, LocalDateTime cutoffTime) {
        NewsEventFeatureVector vector = featureService.buildFeature(stockCode, featureTradeDate, cutoffTime);
        if (vector.getTemporalFeatures() == null || vector.getTemporalFeatures().size() != FEATURE_COUNT) {
            throw new IllegalStateException("新闻特征维度必须为 " + FEATURE_COUNT);
        }
        NewsFeatureSnapshotDocument document = new NewsFeatureSnapshotDocument();
        document.setStockCode(stockCode.trim());
        document.setFeatureTradeDate(featureTradeDate);
        document.setCutoffTime(cutoffTime);
        document.setFeatureVersion(FEATURE_VERSION);
        document.setFeatures(List.copyOf(vector.getTemporalFeatures()));
        document.setSentimentModelVersion(vector.getSentimentModelVersion());
        document.setPipelineVersion(vector.getPipelineVersion());
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
     */
    public Map<String, double[]> loadRequired(
            Collection<String> stockCodes,
            LocalDate startDate,
            LocalDate endDate,
            Set<String> requiredKeys) {
        List<NewsFeatureSnapshotDocument> documents = repository
                .findByFeatureVersionAndStockCodeInAndFeatureTradeDateBetween(
                        FEATURE_VERSION, stockCodes, startDate, endDate);
        Map<String, double[]> result = new LinkedHashMap<>();
        for (NewsFeatureSnapshotDocument document : documents) {
            if (!document.isComplete() || document.getFeatures() == null
                    || document.getFeatures().size() != FEATURE_COUNT
                    || document.getCutoffTime() == null
                    || document.getCutoffTime().toLocalDate().isAfter(document.getFeatureTradeDate())) {
                continue;
            }
            result.put(key(document.getStockCode(), document.getFeatureTradeDate()),
                    document.getFeatures().stream().mapToDouble(Double::doubleValue).toArray());
        }
        List<String> missing = requiredKeys.stream().filter(key -> !result.containsKey(key)).limit(20).toList();
        if (!missing.isEmpty() || result.size() < requiredKeys.size()) {
            throw new IllegalStateException("temporal-news-v1 快照不完整，禁止训练或推理，缺失示例: " + missing);
        }
        return result;
    }

    private String key(String stockCode, LocalDate tradeDate) {
        return stockCode + "|" + tradeDate;
    }
}
// AI_GENERATE_END -
