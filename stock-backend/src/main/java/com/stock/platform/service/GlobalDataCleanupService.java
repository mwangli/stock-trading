// AI_GENERATE_START ---
package com.stock.platform.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.result.DeleteResult;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.entity.ModelOperationAuditDocument;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelTrainingRunDocument;
import com.stock.model.entity.TemporalModelEnsembleStateDocument;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import com.stock.model.repository.ModelOperationAuditRepository;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelTrainingRunRepository;
import com.stock.model.repository.TemporalModelEnsembleStateRepository;
import com.stock.model.repository.TrainingDatasetSnapshotRepository;
import com.stock.schedule.entity.JobExecutionRecord;
import com.stock.schedule.enums.JobExecutionStatus;
import com.stock.schedule.repository.JobExecutionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 全局业务数据清理服务。
 * 按固定保留期清理无引用快照、历史模型制品、新闻派生数据、预测结果和任务审计记录，
 * 每个清理动作独立执行并汇总失败，避免单个数据域异常阻断后续清理。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GlobalDataCleanupService {

    private static final int TRAINING_RUN_RETENTION_DAYS = 365;
    private static final int READY_SNAPSHOT_RETENTION_DAYS = 90;
    private static final int FAILED_SNAPSHOT_RETENTION_DAYS = 30;
    private static final int READY_SNAPSHOTS_PER_CONTRACT = 2;
    private static final int RETIRED_MODEL_ARTIFACT_RETENTION_DAYS = 90;
    private static final int LEGACY_MODEL_VERSIONS_TO_KEEP = 5;
    private static final int NEWS_RETENTION_DAYS = 365;
    private static final int NEWS_DERIVED_RETENTION_DAYS = 730;
    private static final int SHADOW_PREDICTION_RETENTION_DAYS = 365;
    private static final int PREDICTION_RETENTION_DAYS = 365;
    private static final int JOB_EXECUTION_RETENTION_DAYS = 180;
    private static final int MODEL_AUDIT_RETENTION_DAYS = 730;

    private static final List<String> TRAINING_TERMINAL_STATUSES =
            List.of("SUCCEEDED", "FAILED", "CANCELLED", "WAITING_DATA");
    private static final List<String> PREDICTION_TERMINAL_STATUSES =
            List.of("COMPLETED", "PARTIAL", "EVALUATED", "FAILED", "CANCELLED");
    private static final List<String> SHADOW_TERMINAL_STATUSES =
            List.of("EVALUATED", "NO_MARKET_DATA", "FAILED");
    private static final List<JobExecutionStatus> JOB_TERMINAL_STATUSES = List.of(
            JobExecutionStatus.SUCCESS,
            JobExecutionStatus.FAILED,
            JobExecutionStatus.SKIPPED,
            JobExecutionStatus.REJECTED,
            JobExecutionStatus.CANCELLED);

    private final ModelTrainingRunRepository trainingRunRepository;
    private final TrainingDatasetSnapshotRepository snapshotRepository;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository modelActivationRepository;
    private final TemporalModelEnsembleStateRepository ensembleStateRepository;
    private final ModelPredictionBatchRepository predictionBatchRepository;
    private final ModelOperationAuditRepository modelOperationAuditRepository;
    private final JobExecutionRecordRepository jobExecutionRecordRepository;
    private final MongoTemplate mongoTemplate;
    private final GridFsTemplate gridFsTemplate;

    /**
     * 执行全部业务数据清理动作。
     *
     * 所有动作都会尝试执行；存在任一失败时，在全部动作结束后抛出汇总异常，
     * 使 Quartz 执行记录保持失败事实，同时不影响其他清理动作。
     */
    public void cleanupAll() {
        Map<String, CleanupAction> actions = new LinkedHashMap<>();
        actions.put("历史终态训练运行", this::cleanupTrainingRuns);
        actions.put("历史失败训练快照", this::cleanupFailedSnapshots);
        actions.put("无引用 READY 训练快照", this::cleanupReadySnapshots);
        actions.put("RETIRED 模型参数", this::cleanupRetiredModelArtifacts);
        actions.put("孤儿模型参数", this::cleanupOrphanModelArtifacts);
        actions.put("旧版 Mongo LSTM 模型", this::cleanupLegacyMongoModels);
        actions.put("过期原始新闻", this::cleanupExpiredNews);
        actions.put("过期新闻事件事实", this::cleanupExpiredNewsEventFacts);
        actions.put("过期新闻特征快照", this::cleanupExpiredNewsFeatureSnapshots);
        actions.put("过期情感影子预测", this::cleanupExpiredSentimentShadowPredictions);
        actions.put("过期时序模型影子预测", this::cleanupExpiredTemporalShadowPredictions);
        actions.put("过期模型预测批次", this::cleanupPredictionBatches);
        actions.put("历史任务执行记录", this::cleanupJobExecutionRecords);
        actions.put("历史模型操作审计", this::cleanupModelOperationAudits);

        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        long startedAt = System.nanoTime();
        for (Map.Entry<String, CleanupAction> entry : actions.entrySet()) {
            long actionStartedAt = System.nanoTime();
            try {
                long deleted = entry.getValue().execute();
                succeeded++;
                log.info("[全局数据清理] 动作完成: action={}, deleted={}, costMs={}",
                        entry.getKey(), deleted, elapsedMillis(actionStartedAt));
            } catch (Exception exception) {
                failures.add(entry.getKey() + ": " + exception.getClass().getSimpleName());
                log.error("[全局数据清理] 动作失败，继续执行后续动作: action={}, costMs={}",
                        entry.getKey(), elapsedMillis(actionStartedAt), exception);
            }
        }
        log.info("[全局数据清理] 全部动作结束: succeeded={}, failed={}, costMs={}",
                succeeded, failures.size(), elapsedMillis(startedAt));
        if (!failures.isEmpty()) {
            throw new IllegalStateException("部分业务数据清理失败: " + String.join(", ", failures));
        }
    }

    private long cleanupTrainingRuns() {
        List<ModelTrainingRunDocument> records = trainingRunRepository.findByStatusInAndCreatedAtBefore(
                TRAINING_TERMINAL_STATUSES, LocalDateTime.now().minusDays(TRAINING_RUN_RETENTION_DAYS));
        trainingRunRepository.deleteAllInBatch(records);
        return records.size();
    }

    private long cleanupFailedSnapshots() {
        List<TrainingDatasetSnapshotDocument> snapshots = snapshotRepository.findByStatusAndCreatedAtBefore(
                "FAILED", LocalDateTime.now().minusDays(FAILED_SNAPSHOT_RETENTION_DAYS));
        List<TrainingDatasetSnapshotDocument> unreferenced = snapshots.stream()
                .filter(snapshot -> !trainingRunRepository.existsByDatasetSnapshotId(snapshot.getId()))
                .toList();
        for (TrainingDatasetSnapshotDocument snapshot : unreferenced) {
            deleteSnapshotArtifacts(snapshot);
        }
        snapshotRepository.deleteAllInBatch(unreferenced);
        return unreferenced.size();
    }

    private long cleanupReadySnapshots() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(READY_SNAPSHOT_RETENTION_DAYS);
        Map<String, Integer> contractRanks = new HashMap<>();
        List<TrainingDatasetSnapshotDocument> expired = new ArrayList<>();
        for (TrainingDatasetSnapshotDocument snapshot
                : snapshotRepository.findByStatusOrderByCreatedAtDesc("READY")) {
            String contractKey = Objects.toString(snapshot.getModelName(), "") + "|"
                    + Objects.toString(snapshot.getContractHash(), "");
            int rank = contractRanks.merge(contractKey, 1, Integer::sum);
            if (rank <= READY_SNAPSHOTS_PER_CONTRACT
                    || snapshot.getCreatedAt() == null
                    || !snapshot.getCreatedAt().isBefore(cutoff)
                    || trainingRunRepository.existsByDatasetSnapshotId(snapshot.getId())) {
                continue;
            }
            deleteSnapshotArtifacts(snapshot);
            expired.add(snapshot);
        }
        snapshotRepository.deleteAllInBatch(expired);
        return expired.size();
    }

    private long cleanupRetiredModelArtifacts() {
        Set<String> protectedModelIds = protectedModelIds();
        List<LstmModelDocument> retired = lstmModelRepository
                .findByStatusAndCreatedAtBeforeAndParameterFileIdIsNotNull(
                        "RETIRED", LocalDateTime.now().minusDays(RETIRED_MODEL_ARTIFACT_RETENTION_DAYS));
        long deleted = 0;
        for (LstmModelDocument model : retired) {
            if (protectedModelIds.contains(model.getId())) {
                continue;
            }
            deleteGridFsFile(model.getParameterFileId());
            model.setParameterFileId(null);
            model.setParameterSha256(null);
            model.setParameterSize(0L);
            lstmModelRepository.save(model);
            deleted++;
        }
        return deleted;
    }

    private long cleanupOrphanModelArtifacts() {
        Set<String> referencedFiles = new HashSet<>();
        for (LstmModelDocument model : lstmModelRepository.findAll()) {
            if (model.getParameterFileId() != null && !model.getParameterFileId().isBlank()) {
                referencedFiles.add(model.getParameterFileId());
            }
        }
        long deleted = 0;
        Query query = Query.query(Criteria.where("metadata.artifactType").is("LSTM_MODEL_PARAMETERS"));
        for (GridFSFile file : gridFsTemplate.find(query)) {
            String fileId = file.getObjectId().toHexString();
            if (!referencedFiles.contains(fileId)) {
                gridFsTemplate.delete(Query.query(Criteria.where("_id").is(file.getObjectId())));
                deleted++;
            }
        }
        return deleted;
    }

    private long cleanupLegacyMongoModels() {
        String collectionName = "lstm_models";
        if (!mongoTemplate.collectionExists(collectionName)) {
            return 0;
        }
        Query recentQuery = new Query()
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(LEGACY_MODEL_VERSIONS_TO_KEEP);
        recentQuery.fields().include("_id");
        Set<Object> protectedIds = new HashSet<>();
        for (Document document : mongoTemplate.find(recentQuery, Document.class, collectionName)) {
            protectedIds.add(document.get("_id"));
        }
        Query deleteQuery = Query.query(new Criteria().orOperator(
                Criteria.where("status").exists(false),
                Criteria.where("status").ne("ACTIVE")));
        if (!protectedIds.isEmpty()) {
            deleteQuery.addCriteria(Criteria.where("_id").nin(protectedIds));
        }
        return mongoTemplate.remove(deleteQuery, collectionName).getDeletedCount();
    }

    private long cleanupExpiredNews() {
        return removeBefore("stock_news", "publishTime",
                LocalDateTime.now().minusDays(NEWS_RETENTION_DAYS));
    }

    private long cleanupExpiredNewsEventFacts() {
        return removeBefore("stock_news_event_facts", "publishTime",
                LocalDateTime.now().minusDays(NEWS_DERIVED_RETENTION_DAYS));
    }

    private long cleanupExpiredNewsFeatureSnapshots() {
        return removeBefore("news_feature_snapshots", "featureTradeDate",
                LocalDate.now().minusDays(NEWS_DERIVED_RETENTION_DAYS));
    }

    private long cleanupExpiredSentimentShadowPredictions() {
        Query query = Query.query(Criteria.where("predictedAt")
                .lt(LocalDateTime.now().minusDays(SHADOW_PREDICTION_RETENTION_DAYS))
                .and("evaluationStatus").in(SHADOW_TERMINAL_STATUSES));
        return mongoTemplate.remove(query, "sentiment_shadow_predictions").getDeletedCount();
    }

    private long cleanupExpiredTemporalShadowPredictions() {
        Query query = Query.query(Criteria.where("predictedAt")
                .lt(LocalDateTime.now().minusDays(SHADOW_PREDICTION_RETENTION_DAYS))
                .and("evaluationStatus").in(SHADOW_TERMINAL_STATUSES));
        return mongoTemplate.remove(query, "temporal_model_shadow_predictions").getDeletedCount();
    }

    private long cleanupPredictionBatches() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(PREDICTION_RETENTION_DAYS);
        long deleted = 0;
        while (true) {
            List<ModelPredictionBatchDocument> batches = predictionBatchRepository
                    .findTop200ByStatusInAndCreatedAtBeforeOrderByCreatedAtAsc(
                            PREDICTION_TERMINAL_STATUSES, cutoff);
            if (batches.isEmpty()) {
                return deleted;
            }
            List<String> batchIds = batches.stream().map(ModelPredictionBatchDocument::getId).toList();
            mongoTemplate.remove(Query.query(Criteria.where("batchId").in(batchIds)), "model_predictions");
            predictionBatchRepository.deleteAllInBatch(batches);
            deleted += batches.size();
        }
    }

    private long cleanupJobExecutionRecords() {
        List<JobExecutionRecord> records = jobExecutionRecordRepository.findByStatusInAndCreatedAtBefore(
                JOB_TERMINAL_STATUSES, LocalDateTime.now().minusDays(JOB_EXECUTION_RETENTION_DAYS));
        jobExecutionRecordRepository.deleteAllInBatch(records);
        return records.size();
    }

    private long cleanupModelOperationAudits() {
        List<ModelOperationAuditDocument> records = modelOperationAuditRepository.findByCreatedAtBefore(
                LocalDateTime.now().minusDays(MODEL_AUDIT_RETENTION_DAYS));
        modelOperationAuditRepository.deleteAllInBatch(records);
        return records.size();
    }

    private Set<String> protectedModelIds() {
        Set<String> protectedIds = new HashSet<>();
        for (ModelActivationDocument activation : modelActivationRepository.findAll()) {
            addIfPresent(protectedIds, activation.getActiveModelVersionId());
            addIfPresent(protectedIds, activation.getPreviousModelVersionId());
        }
        for (TemporalModelEnsembleStateDocument state : ensembleStateRepository.findAll()) {
            addIfPresent(protectedIds, state.getRankingLstmModelId());
            addIfPresent(protectedIds, state.getMasterModelId());
        }
        return protectedIds;
    }

    private void deleteSnapshotArtifacts(TrainingDatasetSnapshotDocument snapshot) {
        gridFsTemplate.delete(Query.query(Criteria.where("metadata.snapshotId").is(snapshot.getId())));
        deleteGridFsFile(snapshot.getArtifactFileId());
    }

    private void deleteGridFsFile(String fileId) {
        if (fileId == null || !ObjectId.isValid(fileId)) {
            return;
        }
        gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(fileId))));
    }

    private long removeBefore(String collectionName, String fieldName, Object cutoff) {
        if (!mongoTemplate.collectionExists(collectionName)) {
            return 0;
        }
        DeleteResult result = mongoTemplate.remove(
                Query.query(Criteria.where(fieldName).lt(cutoff)), collectionName);
        return result.getDeletedCount();
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    @FunctionalInterface
    private interface CleanupAction {

        long execute();
    }
}
// AI_GENERATE_END ---
