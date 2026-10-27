// AI_GENERATE_START-
package com.stock.model.service;

import com.mongodb.client.gridfs.model.GridFSFile;
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
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 清理模型模块内超过保留期且不再被引用的数据和模型制品。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Service
@RequiredArgsConstructor
public class ModelDataCleanupService {

    private static final int TRAINING_RUN_RETENTION_DAYS = 365;
    private static final int READY_SNAPSHOT_RETENTION_DAYS = 90;
    private static final int FAILED_SNAPSHOT_RETENTION_DAYS = 30;
    private static final int READY_SNAPSHOTS_PER_CONTRACT = 2;
    private static final int RETIRED_MODEL_ARTIFACT_RETENTION_DAYS = 90;
    private static final int LEGACY_MODEL_VERSIONS_TO_KEEP = 5;
    private static final int NEWS_DERIVED_RETENTION_DAYS = 730;
    private static final int SHADOW_PREDICTION_RETENTION_DAYS = 365;
    private static final int PREDICTION_RETENTION_DAYS = 365;
    private static final int MODEL_AUDIT_RETENTION_DAYS = 730;

    private static final List<String> TRAINING_TERMINAL_STATUSES =
            List.of("SUCCEEDED", "FAILED", "CANCELLED", "WAITING_DATA");
    private static final List<String> PREDICTION_TERMINAL_STATUSES =
            List.of("COMPLETED", "PARTIAL", "EVALUATED", "FAILED", "CANCELLED");
    private static final List<String> SHADOW_TERMINAL_STATUSES =
            List.of("EVALUATED", "NO_MARKET_DATA", "FAILED");

    private final ModelTrainingRunRepository trainingRunRepository;
    private final TrainingDatasetSnapshotRepository snapshotRepository;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository modelActivationRepository;
    private final TemporalModelEnsembleStateRepository ensembleStateRepository;
    private final ModelPredictionBatchRepository predictionBatchRepository;
    private final ModelOperationAuditRepository modelOperationAuditRepository;
    private final MongoTemplate mongoTemplate;
    private final GridFsTemplate gridFsTemplate;

    /** 清理超过保留期的终态训练运行。 */
    public long cleanupTrainingRuns() {
        List<ModelTrainingRunDocument> records = trainingRunRepository.findByStatusInAndCreatedAtBefore(
                TRAINING_TERMINAL_STATUSES, LocalDateTime.now().minusDays(TRAINING_RUN_RETENTION_DAYS));
        trainingRunRepository.deleteAllInBatch(records);
        return records.size();
    }

    /** 清理超过保留期且未被引用的失败训练快照。 */
    public long cleanupFailedSnapshots() {
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

    /** 清理超过保留期且未被引用的 READY 训练快照。 */
    public long cleanupReadySnapshots() {
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

    /** 清理已退役且不再受保护的模型参数。 */
    public long cleanupRetiredModelArtifacts() {
        Set<String> protectedModelIds = protectedModelIds();
        List<LstmModelDocument> retired = lstmModelRepository
                .findByStatusAndCreatedAtBeforeAndParameterFileIdIsNotNull(
                        "RETIRED", LocalDateTime.now().minusDays(RETIRED_MODEL_ARTIFACT_RETENTION_DAYS));
        long deleted = 0L;
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

    /** 清理没有模型元数据引用的参数文件。 */
    public long cleanupOrphanModelArtifacts() {
        Set<String> referencedFiles = new HashSet<>();
        for (LstmModelDocument model : lstmModelRepository.findAll()) {
            if (model.getParameterFileId() != null && !model.getParameterFileId().isBlank()) {
                referencedFiles.add(model.getParameterFileId());
            }
        }
        long deleted = 0L;
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

    /** 清理旧版 Mongo LSTM 模型记录并保留最近版本。 */
    public long cleanupLegacyMongoModels() {
        String collectionName = "lstm_models";
        if (!mongoTemplate.collectionExists(collectionName)) {
            return 0L;
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

    /** 清理超过保留期的新闻特征快照。 */
    public long cleanupExpiredNewsFeatureSnapshots() {
        return removeBefore("news_feature_snapshots", "featureTradeDate",
                LocalDate.now().minusDays(NEWS_DERIVED_RETENTION_DAYS));
    }

    /** 清理已终态且超过保留期的情感影子预测。 */
    public long cleanupExpiredSentimentShadowPredictions() {
        Query query = Query.query(Criteria.where("predictedAt")
                .lt(LocalDateTime.now().minusDays(SHADOW_PREDICTION_RETENTION_DAYS))
                .and("evaluationStatus").in(SHADOW_TERMINAL_STATUSES));
        return mongoTemplate.remove(query, "sentiment_shadow_predictions").getDeletedCount();
    }

    /** 清理已终态且超过保留期的时序模型影子预测。 */
    public long cleanupExpiredTemporalShadowPredictions() {
        Query query = Query.query(Criteria.where("predictedAt")
                .lt(LocalDateTime.now().minusDays(SHADOW_PREDICTION_RETENTION_DAYS))
                .and("evaluationStatus").in(SHADOW_TERMINAL_STATUSES));
        return mongoTemplate.remove(query, "temporal_model_shadow_predictions").getDeletedCount();
    }

    /** 清理已终态且超过保留期的模型预测批次及明细。 */
    public long cleanupPredictionBatches() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(PREDICTION_RETENTION_DAYS);
        long deleted = 0L;
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

    /** 清理超过保留期的模型操作审计记录。 */
    public long cleanupModelOperationAudits() {
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
            return 0L;
        }
        return mongoTemplate.remove(
                Query.query(Criteria.where(fieldName).lt(cutoff)), collectionName).getDeletedCount();
    }

    private void addIfPresent(Set<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }
}
// AI_GENERATE_END-