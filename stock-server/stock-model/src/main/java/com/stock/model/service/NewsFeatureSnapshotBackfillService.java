// AI_GENERATE_START-------------
package com.stock.model.service;

import com.stock.data.dto.MarketNewsEventDto;
import com.stock.data.enums.NewsDocumentRole;
import com.stock.data.service.MarketDataQueryService;
import com.stock.data.service.NewsCollectionPipelineService;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.NewsFeatureSnapshotAuditResult;
import com.stock.model.dto.NewsFeatureSnapshotBackfillResult;
import com.stock.model.entity.NewsFeatureSnapshotBackfillState;
import com.stock.model.entity.NewsFeatureSnapshotDocument;
import com.stock.model.repository.NewsFeatureSnapshotBackfillStateRepository;
import com.stock.model.repository.NewsFeatureSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 版本化新闻特征稀疏准备与点时审计服务。
 * 按交易日处理有事件股票并写入日期完备性水位，不再遍历全部模型行情股票日。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsFeatureSnapshotBackfillService {

    private static final int LOOKBACK_DAYS = 5;
    private static final int NEWS_EVENT_PAGE_SIZE = 1_000;
    private static final List<NewsDocumentRole> FEATURE_ROLES = List.of(NewsDocumentRole.TRADE_SIGNAL);

    private final MongoTemplate mongoTemplate;
    private final MarketDataQueryService marketDataQueryService;
    private final LstmTrainingConfig trainingConfig;
    private final NewsFeatureSnapshotService snapshotService;
    private final NewsEventFeatureService featureService;
    private final NewsFeatureCompletenessService completenessService;
    private final NewsFeatureSnapshotRepository snapshotRepository;
    private final NewsFeatureSnapshotBackfillStateRepository stateRepository;

    /** 单次运行最多处理的来源日期数量。 */
    @Value("${app.runtime.news.feature-snapshot-backfill.max-dates-per-run:20}")
    private int maximumDatesPerRun;

    /** 日级新闻快照截止时刻。 */
    @Value("${app.runtime.news.feature-snapshot-backfill.cutoff-time:23:59:59}")
    private LocalTime cutoffTime;

    /**
     * 按交易日分批准备历史有事件稀疏快照和日期水位。
     * 历史新闻治理未完成时拒绝伪造 COMPLETE 水位。
     *
     * @return 本次日期准备和最终审计结果
     * @throws IllegalStateException 历史新闻治理未完成、特征生成或状态持久化失败时抛出
     */
    public synchronized NewsFeatureSnapshotBackfillResult backfill() {
        String source = requireText(trainingConfig.getDataSource(), "模型行情来源不能为空");
        String sourceVersion = requireText(trainingConfig.getDataSourceVersion(), "模型行情版本不能为空");
        if (!marketDataQueryService.isNewsCollectionBackfillComplete(
                NewsCollectionPipelineService.PIPELINE_VERSION)) {
            throw new IllegalStateException("历史新闻清洗和事件事实回填尚未完成，不能写入日期 COMPLETE 水位");
        }

        String stateId = NewsFeatureSnapshotService.FEATURE_VERSION + "|" + source + "|" + sourceVersion;
        NewsFeatureSnapshotBackfillState state = stateRepository.findById(stateId)
                .orElseGet(() -> initialState(stateId));
        state.setStatus("RUNNING");
        state.setLastError(null);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);

        long processedDates = 0L;
        long generatedSnapshots = 0L;
        long existingSnapshots = 0L;
        long failedDates = 0L;
        try {
            List<LocalDate> pendingDates = marketDataQueryService.findModelStockBarDates(source, sourceVersion)
                    .stream()
                    .filter(date -> state.getLastProcessedDate() == null
                            || date.isAfter(state.getLastProcessedDate()))
                    .limit(Math.max(1, maximumDatesPerRun))
                    .toList();
            for (LocalDate featureTradeDate : pendingDates) {
                DatePreparationResult result = prepareDate(featureTradeDate, source, sourceVersion);
                processedDates++;
                generatedSnapshots += result.generatedSnapshotCount();
                existingSnapshots += result.existingSnapshotCount();
                state.setProcessedDateCount(state.getProcessedDateCount() + 1L);
                state.setGeneratedSnapshotCount(
                        state.getGeneratedSnapshotCount() + result.generatedSnapshotCount());
                state.setExistingSnapshotCount(
                        state.getExistingSnapshotCount() + result.existingSnapshotCount());
                state.setLastProcessedDate(featureTradeDate);
                state.setUpdatedAt(LocalDateTime.now());
                stateRepository.save(state);
            }

            boolean hasRemaining = marketDataQueryService.findModelStockBarDates(source, sourceVersion).stream()
                    .anyMatch(date -> state.getLastProcessedDate() == null
                            || date.isAfter(state.getLastProcessedDate()));
            if (!hasRemaining) {
                removeLegacyNoEventSnapshots(source, sourceVersion);
            }
            NewsFeatureSnapshotAuditResult audit = hasRemaining ? null : audit();
            boolean completed = !hasRemaining && audit != null && audit.passed();
            state.setStatus(hasRemaining ? "PENDING" : completed ? "COMPLETED" : "FAILED");
            state.setLastError(completed || hasRemaining ? null : "SPARSE_SNAPSHOT_AUDIT_FAILED");
            state.setUpdatedAt(LocalDateTime.now());
            stateRepository.save(state);
            return new NewsFeatureSnapshotBackfillResult(
                    processedDates, generatedSnapshots, existingSnapshots, failedDates,
                    state.getLastProcessedDate(), completed, audit);
        } catch (RuntimeException exception) {
            failedDates++;
            state.setFailedDateCount(state.getFailedDateCount() + 1L);
            state.setStatus("FAILED");
            state.setLastError(limit(exception.getMessage()));
            state.setUpdatedAt(LocalDateTime.now());
            stateRepository.save(state);
            throw new IllegalStateException("新闻日级特征稀疏准备失败，已保留最后成功日期游标", exception);
        }
    }

    /**
     * 在每日新闻采集、清洗、去重和事件事实同步全部成功后，准备最新模型交易日。
     *
     * @throws IllegalStateException 最新模型交易日不存在或截止时间尚未到达时抛出
     */
    public synchronized void prepareLatestCompletedDate() {
        String source = requireText(trainingConfig.getDataSource(), "模型行情来源不能为空");
        String sourceVersion = requireText(trainingConfig.getDataSourceVersion(), "模型行情版本不能为空");
        LocalDate latestDate = marketDataQueryService.findLatestModelStockBarDate(source, sourceVersion);
        if (latestDate == null) {
            throw new IllegalStateException("没有可准备新闻特征的模型交易日");
        }
        prepareDate(latestDate, source, sourceVersion);
    }

    /**
     * 审计当前行情来源版本的日期水位、稀疏快照契约和点时边界。
     *
     * @return 日期完备性与稀疏快照审计结果
     */
    public NewsFeatureSnapshotAuditResult audit() {
        return audit(NewsFeatureSnapshotService.FEATURE_VERSION, NewsFeatureSnapshotService.FEATURE_COUNT);
    }

    /**
     * 按指定新闻特征契约审计日期水位和稀疏快照完整性。
     *
     * @param featureVersion 新闻特征版本
     * @param featureCount 新闻特征维度
     * @return 日期完备性与稀疏快照审计结果
     */
    public NewsFeatureSnapshotAuditResult audit(String featureVersion, int featureCount) {
        String source = requireText(trainingConfig.getDataSource(), "模型行情来源不能为空");
        String sourceVersion = requireText(trainingConfig.getDataSourceVersion(), "模型行情版本不能为空");
        List<LocalDate> expectedDateValues = marketDataQueryService.findModelStockBarDates(source, sourceVersion);
        long expectedDates = expectedDateValues.size();
        Set<LocalDate> completeDateValues = completenessService.findCompleteDates(
                featureVersion, source, sourceVersion, expectedDateValues);
        long completeDates = completeDateValues.size();

        Criteria snapshotScope = Criteria.where("featureVersion").is(featureVersion)
                .and("marketDataSource").is(source)
                .and("marketDataSourceVersion").is(sourceVersion);
        List<NewsFeatureSnapshotDocument> scopedSnapshots = mongoTemplate.find(
                Query.query(snapshotScope), NewsFeatureSnapshotDocument.class);
        long sparseSnapshots = scopedSnapshots.size();
        long invalidContract = scopedSnapshots.stream()
                .filter(snapshot -> !hasValidSnapshotContract(snapshot, featureCount))
                .count();
        long lookAhead = countSnapshots(new Document("$and", List.of(
                scopeDocument(source, sourceVersion, featureVersion),
                new Document("$expr", new Document("$gt", List.of(
                        "$latestInputPublishTime", "$cutoffTime"))))));
        long missingProvenance = countSnapshots(new Document("$and", List.of(
                scopeDocument(source, sourceVersion, featureVersion),
                new Document("inputEventCount", new Document("$gt", 0)),
                new Document("$or", List.of(
                        new Document("pipelineVersion", new Document("$in", java.util.Arrays.asList(null, ""))),
                        new Document("sentimentModelVersion", new Document("$in", java.util.Arrays.asList(null, ""))))))));
        double dateCompletionRate = expectedDates == 0L ? 0D
                : Math.min(1D, completeDates / (double) expectedDates);
        boolean passed = expectedDates > 0L && completeDateValues.containsAll(expectedDateValues)
                && invalidContract == 0L && lookAhead == 0L && missingProvenance == 0L;
        return new NewsFeatureSnapshotAuditResult(
                expectedDates, completeDates, dateCompletionRate, sparseSnapshots,
                invalidContract, lookAhead, missingProvenance, passed);
    }

    private DatePreparationResult prepareDate(
            LocalDate featureTradeDate, String source, String sourceVersion) {
        LocalDateTime featureCutoff = featureTradeDate.atTime(cutoffTime);
        if (featureCutoff.isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("新闻特征日期截止时间尚未到达: " + featureTradeDate);
        }
        LocalDateTime windowStart = featureCutoff.minusDays(LOOKBACK_DAYS);
        List<MarketNewsEventDto> events = loadFeatureEvents(windowStart, featureCutoff);
        Set<String> eventStockCodes = events.stream()
                .map(MarketNewsEventDto::getStockCode)
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        List<String> sortedEventStockCodes = eventStockCodes.stream().sorted().toList();
        Map<String, List<MarketNewsEventDto>> eventsByStock = events.stream()
                .filter(event -> event.getStockCode() != null && !event.getStockCode().isBlank())
                .collect(Collectors.groupingBy(
                        MarketNewsEventDto::getStockCode,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()));
        String sentimentModelVersion = featureService.preloadSentimentResults(events);
        Map<String, NewsFeatureSnapshotDocument> existingByStock = eventStockCodes.isEmpty()
                ? Map.of()
                : snapshotRepository
                        .findByFeatureVersionAndStockCodeInAndFeatureTradeDateBetween(
                                NewsFeatureSnapshotService.FEATURE_VERSION,
                                sortedEventStockCodes, featureTradeDate, featureTradeDate)
                        .stream()
                        .collect(Collectors.toMap(
                                NewsFeatureSnapshotDocument::getStockCode,
                                Function.identity(),
                                (left, right) -> left,
                                java.util.LinkedHashMap::new));
        List<NewsFeatureSnapshotDocument> pendingSnapshots = new java.util.ArrayList<>();
        long existing = 0L;
        for (String stockCode : sortedEventStockCodes) {
            NewsFeatureSnapshotDocument existingDocument = existingByStock.get(stockCode);
            if (snapshotService.isUsableSnapshot(existingDocument, source, sourceVersion)) {
                existing++;
                continue;
            }
            pendingSnapshots.add(snapshotService.prepareSnapshot(
                    stockCode, featureTradeDate, featureCutoff, source, sourceVersion,
                    eventsByStock.getOrDefault(stockCode, List.of()), sentimentModelVersion,
                    existingDocument));
        }
        if (!pendingSnapshots.isEmpty()) {
            snapshotRepository.saveAll(pendingSnapshots);
        }
        long generated = pendingSnapshots.size();
        long eventCount = events.size();
        LocalDateTime latestInputPublishTime = events.stream()
                .map(MarketNewsEventDto::getPublishTime)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        completenessService.markComplete(
                featureTradeDate, source, sourceVersion, NewsCollectionPipelineService.PIPELINE_VERSION,
                eventCount, eventStockCodes.size(), generated, existing, latestInputPublishTime);
        log.info("新闻特征来源日期准备完成: date={}, eventStocks={}, events={}, generated={}, existing={}",
                featureTradeDate, eventStockCodes.size(), eventCount, generated, existing);
        return new DatePreparationResult(generated, existing);
    }

    private List<MarketNewsEventDto> loadFeatureEvents(
            LocalDateTime windowStart, LocalDateTime featureCutoff) {
        List<MarketNewsEventDto> events = new java.util.ArrayList<>();
        for (NewsDocumentRole role : FEATURE_ROLES) {
            for (int page = 0; ; page++) {
                List<MarketNewsEventDto> batch = marketDataQueryService.findNewsEvents(
                        role, windowStart, featureCutoff, page, NEWS_EVENT_PAGE_SIZE);
                events.addAll(batch);
                if (batch.size() < NEWS_EVENT_PAGE_SIZE) {
                    break;
                }
            }
        }
        return events;
    }

    private boolean hasValidSnapshotContract(
            NewsFeatureSnapshotDocument snapshot, int featureCount) {
        if (snapshot == null || !snapshot.isComplete()
                || snapshot.getStockCode() == null || snapshot.getStockCode().isBlank()
                || snapshot.getCutoffTime() == null || snapshot.getFeatureTradeDate() == null
                || !snapshot.getCutoffTime().toLocalDate().equals(snapshot.getFeatureTradeDate())
                || snapshot.getInputEventCount() <= 0
                || snapshot.getFeatures() == null || snapshot.getFeatures().size() != featureCount) {
            return false;
        }
        for (int index = 0; index < snapshot.getFeatures().size(); index++) {
            Double value = snapshot.getFeatures().get(index);
            double minimum = index == 0 ? -1D : 0D;
            if (value == null || !Double.isFinite(value) || value < minimum || value > 1D) {
                return false;
            }
        }
        return true;
    }

    private long removeLegacyNoEventSnapshots(String source, String sourceVersion) {
        Criteria legacyNoEvent = new Criteria().andOperator(
                Criteria.where("featureVersion").is(NewsFeatureSnapshotService.FEATURE_VERSION),
                Criteria.where("marketDataSource").is(source),
                Criteria.where("marketDataSourceVersion").is(sourceVersion),
                Criteria.where("complete").is(true),
                Criteria.where("inputEventCount").lte(0),
                Criteria.where("features").is(java.util.Collections.nCopies(
                        NewsFeatureSnapshotService.FEATURE_COUNT, 0D)),
                Criteria.where("pipelineVersion").in(java.util.Arrays.asList(null, "")),
                Criteria.where("sentimentModelVersion").in(java.util.Arrays.asList(null, "")));
        long removed = mongoTemplate.remove(
                Query.query(legacyNoEvent), NewsFeatureSnapshotDocument.class).getDeletedCount();
        if (removed > 0L) {
            log.info("已删除旧式全零 NO_EVENT 新闻快照: source={}, version={}, count={}",
                    source, sourceVersion, removed);
        }
        return removed;
    }

    private long countSnapshots(Document queryDocument) {
        return mongoTemplate.count(new BasicQuery(queryDocument), NewsFeatureSnapshotDocument.class);
    }

    private Document scopeDocument(String source, String sourceVersion, String featureVersion) {
        return new Document("featureVersion", featureVersion)
                .append("marketDataSource", source)
                .append("marketDataSourceVersion", sourceVersion);
    }

    private NewsFeatureSnapshotBackfillState initialState(String stateId) {
        NewsFeatureSnapshotBackfillState state = new NewsFeatureSnapshotBackfillState();
        state.setId(stateId);
        state.setStatus("PENDING");
        state.setUpdatedAt(LocalDateTime.now());
        return state;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value.trim();
    }

    private String limit(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN_SPARSE_SNAPSHOT_PREPARATION_ERROR";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private record DatePreparationResult(long generatedSnapshotCount, long existingSnapshotCount) {
    }
}
// AI_GENERATE_END-------------
