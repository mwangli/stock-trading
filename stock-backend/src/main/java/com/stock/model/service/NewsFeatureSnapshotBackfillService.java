// AI_GENERATE_START -
package com.stock.model.service;

import com.stock.market.entity.ModelStockDailyBar;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * `temporal-news-v1` 历史股票日级快照回填与点时审计服务。
 * 按模型日线 ID 游标顺序执行小批量、幂等生成，适配 2C4G 节点顺序推理。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NewsFeatureSnapshotBackfillService {

    private static final double MINIMUM_COVERAGE_RATE = 0.995D;

    private final MongoTemplate mongoTemplate;
    private final LstmTrainingConfig trainingConfig;
    private final NewsFeatureSnapshotService snapshotService;
    private final NewsFeatureSnapshotRepository snapshotRepository;
    private final NewsFeatureSnapshotBackfillStateRepository stateRepository;

    /** 单批处理的股票日数量。 */
    @Value("${app.runtime.news.feature-snapshot-backfill.batch-size:200}")
    private int batchSize;

    /** 单次任务最多处理的股票日数量。 */
    @Value("${app.runtime.news.feature-snapshot-backfill.max-documents-per-run:2000}")
    private int maximumDocumentsPerRun;

    /** 日级新闻快照截止时刻。 */
    @Value("${app.runtime.news.feature-snapshot-backfill.cutoff-time:23:59:59}")
    private LocalTime cutoffTime;

    /**
     * 分批生成当前模型行情来源版本要求的全部新闻日级快照。
     *
     * @return 本次回填和最终审计结果
     * @throws IllegalStateException 快照生成或状态持久化失败时抛出
     */
    public synchronized NewsFeatureSnapshotBackfillResult backfill() {
        String source = requireText(trainingConfig.getDataSource(), "模型行情来源不能为空");
        String sourceVersion = requireText(trainingConfig.getDataSourceVersion(), "模型行情版本不能为空");
        String stateId = NewsFeatureSnapshotService.FEATURE_VERSION + "|" + source + "|" + sourceVersion;
        NewsFeatureSnapshotBackfillState state = stateRepository.findById(stateId)
                .orElseGet(() -> initialState(stateId));
        state.setStatus("RUNNING");
        state.setLastError(null);
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);

        long scanned = 0L;
        long generated = 0L;
        long skipped = 0L;
        long failed = 0L;
        try {
            while (scanned < Math.max(1, maximumDocumentsPerRun)) {
                int limit = (int) Math.min(Math.max(1, batchSize), maximumDocumentsPerRun - scanned);
                List<ModelStockDailyBar> bars = loadNextBars(
                        state.getLastProcessedBarId(), source, sourceVersion, limit);
                if (bars.isEmpty()) {
                    NewsFeatureSnapshotAuditResult audit = audit();
                    state.setStatus(audit.passed() ? "COMPLETED" : "FAILED");
                    state.setLastError(audit.passed() ? null : "SNAPSHOT_AUDIT_FAILED");
                    state.setUpdatedAt(LocalDateTime.now());
                    stateRepository.save(state);
                    return new NewsFeatureSnapshotBackfillResult(
                            scanned, generated, skipped, failed,
                            state.getLastProcessedBarId(), audit.passed(), audit);
                }
                for (ModelStockDailyBar bar : bars) {
                    if (bar.getId() == null || bar.getStockCode() == null || bar.getStockCode().isBlank()
                            || bar.getTradeDate() == null) {
                        failed++;
                        state.setFailedCount(state.getFailedCount() + 1);
                    } else if (snapshotRepository
                            .existsByFeatureVersionAndMarketDataSourceAndMarketDataSourceVersionAndStockCodeAndFeatureTradeDate(
                                    NewsFeatureSnapshotService.FEATURE_VERSION, source, sourceVersion,
                                    bar.getStockCode(), bar.getTradeDate())) {
                        skipped++;
                        state.setSkippedCount(state.getSkippedCount() + 1);
                    } else {
                        snapshotService.generateSnapshot(
                                bar.getStockCode(), bar.getTradeDate(), bar.getTradeDate().atTime(cutoffTime),
                                source, sourceVersion);
                        generated++;
                        state.setGeneratedCount(state.getGeneratedCount() + 1);
                    }
                    scanned++;
                    state.setScannedCount(state.getScannedCount() + 1);
                    state.setLastProcessedBarId(bar.getId());
                }
                state.setUpdatedAt(LocalDateTime.now());
                stateRepository.save(state);
                log.info("新闻快照回填批次完成: source={}, version={}, scanned={}, generated={}, skipped={}, lastBarId={}",
                        source, sourceVersion, state.getScannedCount(), state.getGeneratedCount(),
                        state.getSkippedCount(), state.getLastProcessedBarId());
            }
            state.setStatus("PENDING");
            state.setUpdatedAt(LocalDateTime.now());
            stateRepository.save(state);
            return new NewsFeatureSnapshotBackfillResult(
                    scanned, generated, skipped, failed,
                    state.getLastProcessedBarId(), false, null);
        } catch (RuntimeException exception) {
            state.setStatus("FAILED");
            state.setLastError(limit(exception.getMessage()));
            state.setUpdatedAt(LocalDateTime.now());
            stateRepository.save(state);
            throw new IllegalStateException("新闻日级特征快照回填失败，已保留最后成功游标", exception);
        }
    }

    /**
     * 审计当前行情来源版本的快照覆盖率、点时边界和追溯字段。
     *
     * @return 点时与覆盖率审计结果
     */
    public NewsFeatureSnapshotAuditResult audit() {
        String source = requireText(trainingConfig.getDataSource(), "模型行情来源不能为空");
        String sourceVersion = requireText(trainingConfig.getDataSourceVersion(), "模型行情版本不能为空");
        Query expectedQuery = Query.query(Criteria.where("source").is(source)
                .and("sourceVersion").is(sourceVersion));
        long expected = mongoTemplate.count(expectedQuery, ModelStockDailyBar.class);
        Criteria snapshotScope = Criteria.where("featureVersion").is(NewsFeatureSnapshotService.FEATURE_VERSION)
                .and("marketDataSource").is(source)
                .and("marketDataSourceVersion").is(sourceVersion);
        long actual = mongoTemplate.count(Query.query(snapshotScope), NewsFeatureSnapshotDocument.class);
        long invalidContract = countSnapshots(new Document("$and", List.of(
                scopeDocument(source, sourceVersion),
                new Document("$or", List.of(
                        new Document("complete", new Document("$ne", true)),
                        new Document("cutoffTime", null),
                        new Document("featureTradeDate", null),
                        new Document("$expr", new Document("$ne", List.of(new Document("$size", new Document("$ifNull", List.of("$features", List.of()))), 25))))))));
        long lookAhead = countSnapshots(new Document("$and", List.of(
                scopeDocument(source, sourceVersion),
                new Document("$expr", new Document("$gt", List.of("$latestInputPublishTime", "$cutoffTime"))))));
        long missingProvenance = countSnapshots(new Document("$and", List.of(
                scopeDocument(source, sourceVersion),
                new Document("inputEventCount", new Document("$gt", 0)),
                new Document("$or", List.of(
                        new Document("pipelineVersion", new Document("$in", java.util.Arrays.asList(null, ""))),
                        new Document("sentimentModelVersion", new Document("$in", java.util.Arrays.asList(null, ""))))))));
        double coverageRate = expected == 0L ? 0D : Math.min(1D, actual / (double) expected);
        boolean passed = expected > 0L && coverageRate >= MINIMUM_COVERAGE_RATE
                && invalidContract == 0L && lookAhead == 0L && missingProvenance == 0L;
        return new NewsFeatureSnapshotAuditResult(
                expected, actual, coverageRate, invalidContract, lookAhead, missingProvenance, passed);
    }

    private List<ModelStockDailyBar> loadNextBars(
            String lastProcessedId, String source, String sourceVersion, int limit) {
        Criteria sourceCriteria = Criteria.where("source").is(source).and("sourceVersion").is(sourceVersion);
        Criteria criteria = lastProcessedId == null || lastProcessedId.isBlank()
                ? sourceCriteria
                : new Criteria().andOperator(sourceCriteria, Criteria.where("_id").gt(lastProcessedId));
        return mongoTemplate.find(Query.query(criteria)
                .with(Sort.by(Sort.Direction.ASC, "_id"))
                .limit(Math.max(1, limit)), ModelStockDailyBar.class);
    }

    private long countSnapshots(Document queryDocument) {
        return mongoTemplate.count(new BasicQuery(queryDocument), NewsFeatureSnapshotDocument.class);
    }

    private Document scopeDocument(String source, String sourceVersion) {
        return new Document("featureVersion", NewsFeatureSnapshotService.FEATURE_VERSION)
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
            return "UNKNOWN_SNAPSHOT_BACKFILL_ERROR";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
// AI_GENERATE_END -
