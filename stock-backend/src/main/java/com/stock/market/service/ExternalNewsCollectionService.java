// AI_GENERATE_START ---
package com.stock.market.service;

import com.stock.market.client.news.ExternalNewsHttpSupport;
import com.stock.market.client.news.ExternalNewsSourceClient;
import com.stock.market.dto.ExternalNewsCollectionResult;
import com.stock.market.dto.ExternalNewsRecord;
import com.stock.market.dto.NewsEventFactSyncResult;
import com.stock.market.entity.StockInfo;
import com.stock.market.entity.StockNews;
import com.stock.market.enums.NewsQualityStatus;
import com.stock.market.repository.NewsRepository;
import com.stock.market.repository.StockInfoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部新闻来源增量采集编排服务。
 * 在总开关开启后顺序调用独立来源客户端，统一执行 V5 治理、幂等入库和事件事实同步。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalNewsCollectionService {

    private final List<ExternalNewsSourceClient> sourceClients;
    private final StockInfoRepository stockInfoRepository;
    private final NewsRepository newsRepository;
    private final NewsCollectionPipelineService pipelineService;
    private final NewsEventFactService eventFactService;
    private final ExternalNewsHttpSupport httpSupport;

    @Value("${app.runtime.news.external.enabled:false}")
    private boolean enabled;

    @Value("${app.runtime.news.external.max-records-per-source:500}")
    private int maxRecordsPerSource;

    /**
     * 执行一次外部新闻来源增量采集。
     * 单一来源失败只记录错误；全部启用来源失败时由调用方根据结果决定任务状态。
     *
     * @return 来源成功数、解析数、实际新增数和事件事实统计
     */
    public ExternalNewsCollectionResult collectIncremental() {
        if (!enabled) {
            return ExternalNewsCollectionResult.disabled();
        }
        List<ExternalNewsSourceClient> enabledClients = sourceClients.stream()
                .filter(ExternalNewsSourceClient::isEnabled)
                .sorted(Comparator.comparing(ExternalNewsSourceClient::sourceKey))
                .toList();
        if (enabledClients.isEmpty()) {
            return new ExternalNewsCollectionResult(true, 0, 0, 0, 0, 0L,
                    List.of("未启用任何外部新闻来源"));
        }

        List<StockInfo> stocks = stockInfoRepository.findAll();
        Map<String, ExternalNewsRecord> uniqueRecords = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        int successfulSources = 0;
        int fetchedCount = 0;
        int sourceLimit = Math.max(1, maxRecordsPerSource);
        for (ExternalNewsSourceClient client : enabledClients) {
            try {
                List<ExternalNewsRecord> records = client.fetch(stocks, sourceLimit);
                fetchedCount += records.size();
                for (ExternalNewsRecord record : records) {
                    if (isUsable(record)) {
                        uniqueRecords.putIfAbsent(uniqueKey(record), record);
                    }
                }
                successfulSources++;
                log.info("外部新闻来源采集完成: source={}, fetched={}, accepted={}",
                        client.sourceKey(), records.size(), uniqueRecords.size());
            } catch (Exception exception) {
                String error = client.sourceKey() + ": " + exception.getMessage();
                errors.add(error);
                log.warn("外部新闻来源采集失败: source={}, error={}",
                        client.sourceKey(), exception.getMessage());
            }
        }

        List<StockNews> candidates = uniqueRecords.values().stream()
                .map(this::toStockNews)
                .toList();
        Map<String, StockNews> existingById = new LinkedHashMap<>();
        if (!candidates.isEmpty()) {
            newsRepository.findAllById(candidates.stream().map(StockNews::getId).toList())
                    .forEach(news -> existingById.put(news.getId(), news));
        }
        List<StockNews> inserted = candidates.stream()
                .filter(news -> !existingById.containsKey(news.getId()))
                .toList();
        if (!inserted.isEmpty()) {
            newsRepository.saveAll(inserted);
        }
        List<StockNews> documentsToSynchronize = new ArrayList<>(inserted);
        candidates.stream()
                .filter(news -> {
                    StockNews existing = existingById.get(news.getId());
                    return existing != null && existing.getQualityStatus() == NewsQualityStatus.FACT_PENDING;
                })
                .forEach(documentsToSynchronize::add);
        NewsEventFactSyncResult factResult = eventFactService.synchronizeBatch(documentsToSynchronize);
        return new ExternalNewsCollectionResult(true, enabledClients.size(), successfulSources,
                fetchedCount, inserted.size(), factResult.eligibleDocumentCount(), List.copyOf(errors));
    }

    private boolean isUsable(ExternalNewsRecord record) {
        return record != null
                && record.sourceKey() != null && !record.sourceKey().isBlank()
                && record.externalId() != null && !record.externalId().isBlank()
                && record.title() != null && !record.title().isBlank()
                && record.stockCode() != null && record.stockCode().matches("\\d{6}")
                && record.source() != null && !record.source().isBlank()
                && record.publishTime() != null;
    }

    private String uniqueKey(ExternalNewsRecord record) {
        return record.sourceKey() + "|" + record.externalId() + "|" + record.stockCode();
    }

    private StockNews toStockNews(ExternalNewsRecord record) {
        StockNews news = new StockNews();
        news.setId(httpSupport.sha256(uniqueKey(record)));
        news.setExternalId(record.sourceKey() + ":" + record.externalId());
        news.setTitle(record.title());
        news.setContent(record.content());
        news.setRawTitle(record.title());
        news.setRawContent(record.content());
        news.setStockCode(record.stockCode());
        news.setStockName(record.stockName());
        news.setSource(record.source());
        news.setCategory(record.channel());
        news.setRawMenuId(record.sourceKey());
        news.setSourceChannel(record.channel());
        news.setSourceUrl(record.sourceUrl());
        news.setPublishTime(record.publishTime());
        news.setCreateTime(LocalDateTime.now());
        return pipelineService.process(news);
    }
}
// AI_GENERATE_END ---
