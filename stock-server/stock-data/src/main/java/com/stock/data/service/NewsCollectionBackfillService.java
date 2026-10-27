// AI_GENERATE_START-----
package com.stock.data.service;

import com.stock.data.dto.NewsCollectionBackfillResult;
import com.stock.data.dto.NewsEventFactSyncResult;
import com.stock.data.entity.NewsCollectionBackfillState;
import com.stock.data.entity.StockNews;
import com.stock.data.enums.NewsBackfillStatus;
import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.enums.NewsQualityStatus;
import com.stock.data.repository.NewsCollectionBackfillStateRepository;
import com.stock.data.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 历史新闻采集治理回填服务。
 * 复用在线采集管线，按 MongoDB 文档 ID 游标幂等补齐治理字段和事件事实。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsCollectionBackfillService {

    private static final int MAX_DOCUMENT_RETRIES = 3;

    private final MongoTemplate mongoTemplate;
    private final NewsRepository newsRepository;
    private final NewsCollectionBackfillStateRepository stateRepository;
    private final NewsCollectionPipelineService pipelineService;
    private final NewsEventFactService eventFactService;

    /** 单批处理文档数。 */
    @Value("${app.runtime.news.collection-backfill.batch-size:500}")
    private int batchSize;

    /** 单次任务最多处理文档数，控制 2C4G 节点资源占用。 */
    @Value("${app.runtime.news.collection-backfill.max-documents-per-run:50000}")
    private int maximumDocumentsPerRun;

    /**
     * 执行当前管线版本的历史新闻回填。
     *
     * @return 当前任务运行统计和持久化游标
     * @throws IllegalStateException 原始文档或事件事实持久化失败时抛出
     */
    public NewsCollectionBackfillResult backfill() {
        String version = NewsCollectionPipelineService.PIPELINE_VERSION;
        NewsCollectionBackfillState state = stateRepository.findById(version)
                .orElseGet(() -> initialState(version));
        if (state.getStatus() == NewsBackfillStatus.COMPLETED && !hasRemainingWork()) {
            return result(state, 0, 0, 0, 0, 0, Map.of(), Map.of(), 0, true);
        }
        if (state.getStatus() == NewsBackfillStatus.COMPLETED) {
            state.setStatus(NewsBackfillStatus.PENDING);
            state.setLastProcessedId(null);
        }

        state.setStatus(NewsBackfillStatus.RUNNING);
        state.setLastError(null);
        state.setUpdateTime(LocalDateTime.now());
        stateRepository.save(state);

        long scanned = 0;
        long updated = 0;
        long eventFacts = 0;
        long duplicates = 0;
        long ignored = 0;
        long activeBatchSize = 0;
        Map<String, Long> documentTypeCounts = new LinkedHashMap<>();
        Map<String, Long> documentRoleCounts = new LinkedHashMap<>();
        try {
            while (scanned < maximumDocumentsPerRun) {
                int currentBatchSize = (int) Math.min(batchSize, maximumDocumentsPerRun - scanned);
                List<StockNews> batch = loadNextBatch(state.getLastProcessedId(), currentBatchSize);
                if (batch.isEmpty()) {
                    if (hasRemainingWork() && state.getLastProcessedId() != null) {
                        state.setLastProcessedId(null);
                        state.setUpdateTime(LocalDateTime.now());
                        stateRepository.save(state);
                        continue;
                    }
                    state.setStatus(NewsBackfillStatus.COMPLETED);
                    state.setUpdateTime(LocalDateTime.now());
                    stateRepository.save(state);
                    return result(state, scanned, updated, eventFacts, duplicates, ignored,
                            documentTypeCounts, documentRoleCounts, 0, true);
                }
                activeBatchSize = batch.size();

                for (StockNews news : batch) {
                    processWithIsolation(news, state);
                }
                newsRepository.saveAll(batch);
                List<StockNews> factCandidates = batch.stream()
                        .filter(news -> news.getQualityStatus() != NewsQualityStatus.INVALID)
                        .toList();
                NewsEventFactSyncResult factResult = eventFactService.synchronizeBatch(factCandidates);
                long ignoredInBatch = batch.stream()
                        .filter(news -> news.getDocumentRole() == NewsDocumentRole.IGNORE)
                        .count();
                Map<String, Long> typeCountsInBatch = countBy(
                        batch, news -> enumName(news.getDocumentType()));
                Map<String, Long> roleCountsInBatch = countBy(
                        batch, news -> enumName(news.getDocumentRole()));
                String lastProcessedId = batch.get(batch.size() - 1).getId();

                scanned += batch.size();
                updated += batch.size();
                eventFacts += factResult.insertedEventCount();
                duplicates += factResult.duplicateDocumentCount();
                ignored += ignoredInBatch;
                mergeCounts(documentTypeCounts, typeCountsInBatch);
                mergeCounts(documentRoleCounts, roleCountsInBatch);
                state.setLastProcessedId(lastProcessedId);
                state.setScannedCount(state.getScannedCount() + batch.size());
                state.setUpdatedCount(state.getUpdatedCount() + batch.size());
                state.setEventFactCount(state.getEventFactCount() + factResult.insertedEventCount());
                state.setDuplicateCount(state.getDuplicateCount() + factResult.duplicateDocumentCount());
                state.setIgnoredCount(state.getIgnoredCount() + ignoredInBatch);
                state.setDocumentTypeCounts(mergedCopy(state.getDocumentTypeCounts(), typeCountsInBatch));
                state.setDocumentRoleCounts(mergedCopy(state.getDocumentRoleCounts(), roleCountsInBatch));
                state.setUpdateTime(LocalDateTime.now());
                stateRepository.save(state);
                activeBatchSize = 0;
                log.info("新闻采集治理回填批次完成: version={}, scanned={}, lastProcessedId={}",
                        version, state.getScannedCount(), lastProcessedId);
            }
            state.setStatus(NewsBackfillStatus.PENDING);
            state.setUpdateTime(LocalDateTime.now());
            stateRepository.save(state);
            return result(state, scanned, updated, eventFacts, duplicates, ignored,
                    documentTypeCounts, documentRoleCounts, 0, false);
        } catch (RuntimeException exception) {
            state.setStatus(NewsBackfillStatus.FAILED);
            state.setFailedCount(state.getFailedCount() + Math.max(1, activeBatchSize));
            state.setLastError(limitError(exception.getMessage()));
            state.setUpdateTime(LocalDateTime.now());
            stateRepository.save(state);
            throw new IllegalStateException("历史新闻采集治理回填失败，已保留最后成功游标", exception);
        }
    }

    private List<StockNews> loadNextBatch(String lastProcessedId, int limit) {
        Criteria needsProcessing = new Criteria().orOperator(
                Criteria.where("pipelineVersion").ne(NewsCollectionPipelineService.PIPELINE_VERSION),
                Criteria.where("qualityStatus").in(NewsQualityStatus.FACT_PENDING, null));
        Criteria criteria = lastProcessedId == null || lastProcessedId.isBlank()
                ? needsProcessing
                : new Criteria().andOperator(Criteria.where("_id").gt(lastProcessedId), needsProcessing);
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(Math.max(1, limit));
        return mongoTemplate.find(query, StockNews.class, "stock_news");
    }

    private boolean hasRemainingWork() {
        Criteria criteria = new Criteria().orOperator(
                Criteria.where("pipelineVersion").ne(NewsCollectionPipelineService.PIPELINE_VERSION),
                Criteria.where("qualityStatus").in(NewsQualityStatus.FACT_PENDING, null));
        return mongoTemplate.exists(Query.query(criteria), StockNews.class, "stock_news");
    }

    private void processWithIsolation(StockNews news, NewsCollectionBackfillState state) {
        try {
            pipelineService.process(news);
            if (news.getId() != null && state.getFailureRetryCounts() != null) {
                state.getFailureRetryCounts().remove(news.getId());
            }
        } catch (RuntimeException exception) {
            String documentId = news.getId() == null ? "UNKNOWN" : news.getId();
            Map<String, Integer> retries = state.getFailureRetryCounts();
            if (retries == null) {
                retries = new LinkedHashMap<>();
                state.setFailureRetryCounts(retries);
            }
            int retryCount = retries.merge(documentId, 1, Integer::sum);
            if (retryCount < MAX_DOCUMENT_RETRIES) {
                throw exception;
            }
            news.setPipelineVersion(NewsCollectionPipelineService.PIPELINE_VERSION);
            news.setQualityStatus(NewsQualityStatus.INVALID);
            news.setQualityReason("PIPELINE_ERROR_AFTER_" + MAX_DOCUMENT_RETRIES + "_RETRIES");
            retries.remove(documentId);
            state.setFailedCount(state.getFailedCount() + 1);
            log.error("新闻治理文档连续失败并隔离: documentId={}, retries={}, reason={}",
                    documentId, retryCount, limitError(exception.getMessage()));
        }
    }

    private NewsCollectionBackfillState initialState(String version) {
        NewsCollectionBackfillState state = new NewsCollectionBackfillState();
        state.setPipelineVersion(version);
        state.setStatus(NewsBackfillStatus.PENDING);
        state.setUpdateTime(LocalDateTime.now());
        return state;
    }

    private NewsCollectionBackfillResult result(NewsCollectionBackfillState state,
                                                long scanned, long updated, long eventFacts, long duplicates,
                                                long ignored, Map<String, Long> documentTypeCounts,
                                                Map<String, Long> documentRoleCounts,
                                                long failed, boolean completed) {
        return new NewsCollectionBackfillResult(
                state.getPipelineVersion(), scanned, updated, eventFacts, duplicates, ignored,
                Map.copyOf(documentTypeCounts), Map.copyOf(documentRoleCounts), failed,
                state.getLastProcessedId(), completed);
    }

    private Map<String, Long> countBy(List<StockNews> batch, Function<StockNews, String> classifier) {
        return batch.stream().collect(Collectors.groupingBy(
                classifier, LinkedHashMap::new, Collectors.counting()));
    }

    private String enumName(Enum<?> value) {
        return value == null ? "UNKNOWN" : value.name();
    }

    private Map<String, Long> mergedCopy(Map<String, Long> current, Map<String, Long> additions) {
        Map<String, Long> merged = new LinkedHashMap<>();
        if (current != null) {
            merged.putAll(current);
        }
        mergeCounts(merged, additions);
        return merged;
    }

    private void mergeCounts(Map<String, Long> target, Map<String, Long> additions) {
        additions.forEach((key, value) -> target.merge(key, value, Long::sum));
    }

    private String limitError(String message) {
        if (message == null) {
            return "UNKNOWN_BACKFILL_ERROR";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
// AI_GENERATE_END-----
