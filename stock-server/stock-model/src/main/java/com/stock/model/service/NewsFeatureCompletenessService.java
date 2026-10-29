// AI_GENERATE_START --
package com.stock.model.service;

import com.stock.model.entity.NewsFeatureCompletenessDocument;
import com.stock.model.repository.NewsFeatureCompletenessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 新闻特征日期完备性水位服务。
 * 统一维护 COMPLETE 日期，并为稀疏新闻快照读取提供 NO_EVENT 判定依据。
 *
 * @author mwangli
 * @since 2026-10-28
 */
@Service
@RequiredArgsConstructor
public class NewsFeatureCompletenessService {

    /** 日期已经完成全部新闻特征准备。 */
    public static final String STATUS_COMPLETE = "COMPLETE";

    private final NewsFeatureCompletenessRepository repository;

    /**
     * 查询指定来源版本中已完成的日期集合。
     *
     * @param featureVersion 新闻特征版本
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @param dates 待检查日期
     * @return 状态为 COMPLETE 的日期集合
     */
    public Set<LocalDate> findCompleteDates(
            String featureVersion,
            String marketDataSource,
            String marketDataSourceVersion,
            Collection<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) {
            return Set.of();
        }
        Set<LocalDate> uniqueDates = new LinkedHashSet<>(dates);
        Set<LocalDate> completeDates = new LinkedHashSet<>();
        repository.findByFeatureVersionAndMarketDataSourceAndMarketDataSourceVersionAndFeatureTradeDateIn(
                        featureVersion, marketDataSource, marketDataSourceVersion, uniqueDates).stream()
                .filter(item -> STATUS_COMPLETE.equals(item.getStatus()))
                .map(NewsFeatureCompletenessDocument::getFeatureTradeDate)
                .forEach(completeDates::add);
        return Set.copyOf(completeDates);
    }

    /**
     * 将来源日期标记为完成，并保存可审计的准备统计。
     *
     * @param featureTradeDate 特征交易日
     * @param marketDataSource 模型行情数据来源
     * @param marketDataSourceVersion 模型行情数据版本
     * @param pipelineVersion 新闻治理管线版本
     * @param deduplicatedEventCount 去重后的有效事件数
     * @param eventStockCount 有事件股票数
     * @param generatedSnapshotCount 新生成稀疏快照数
     * @param existingSnapshotCount 复用已有稀疏快照数
     * @param latestInputPublishTime 最晚输入事件发布时间
     * @return 已保存水位
     */
    public NewsFeatureCompletenessDocument markComplete(
            LocalDate featureTradeDate,
            String marketDataSource,
            String marketDataSourceVersion,
            String pipelineVersion,
            long deduplicatedEventCount,
            long eventStockCount,
            long generatedSnapshotCount,
            long existingSnapshotCount,
            LocalDateTime latestInputPublishTime) {
        String id = NewsFeatureSnapshotService.FEATURE_VERSION + "|" + marketDataSource + "|"
                + marketDataSourceVersion + "|" + featureTradeDate;
        NewsFeatureCompletenessDocument document = repository.findById(id)
                .orElseGet(NewsFeatureCompletenessDocument::new);
        document.setId(id);
        document.setFeatureVersion(NewsFeatureSnapshotService.FEATURE_VERSION);
        document.setMarketDataSource(marketDataSource);
        document.setMarketDataSourceVersion(marketDataSourceVersion);
        document.setFeatureTradeDate(featureTradeDate);
        document.setStatus(STATUS_COMPLETE);
        document.setPipelineVersion(pipelineVersion);
        document.setDeduplicatedEventCount(deduplicatedEventCount);
        document.setEventStockCount(eventStockCount);
        document.setGeneratedSnapshotCount(generatedSnapshotCount);
        document.setExistingSnapshotCount(existingSnapshotCount);
        document.setFailedCount(0L);
        document.setLatestInputPublishTime(latestInputPublishTime);
        document.setCompletedAt(LocalDateTime.now());
        document.setLastError(null);
        return repository.save(document);
    }
}
// AI_GENERATE_END --
