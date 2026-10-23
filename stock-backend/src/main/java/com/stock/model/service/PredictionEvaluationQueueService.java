// AI_GENERATE_START--
package com.stock.model.service;

import com.stock.data.repository.PriceRepository;
import com.stock.model.config.PredictionBackfillProperties;
import com.stock.model.dto.PredictionEvaluationRequest;
import com.stock.model.dto.PredictionOperationResponse;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelPredictionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模型预测评估持久化队列服务。
 *
 * 负责评估入队条件、队列消费、失败重试和行情同步后的批次唤醒。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
public class PredictionEvaluationQueueService {

    private static final List<String> EVALUATABLE_STATUSES = List.of("COMPLETED", "PARTIAL", "EVALUATED");
    private static final String WAIT_TARGET_DATE = "TARGET_DATE_NOT_REACHED";
    private static final String WAIT_MARKET_DATA = "MARKET_DATA_COVERAGE_LOW";
    private static final String WAIT_PENDING_DETAILS = "PENDING_MARKET_DATA";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final PredictionBackfillProperties backfillProperties;
    private final PriceRepository priceRepository;
    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;
    private final ModelPredictionQueryService queryService;
    private final PredictionEvaluationService evaluationService;
    private final MongoTemplate mongoTemplate;
    private final Executor queueExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 创建预测评估队列服务。
     *
     * @param backfillProperties 预测补缺配置
     * @param priceRepository 日线行情仓库
     * @param batchRepository 预测批次仓库
     * @param predictionRepository 预测明细仓库
     * @param queryService 预测查询服务
     * @param evaluationService 预测评估服务
     * @param mongoTemplate MongoDB 操作模板
     * @param queueExecutor 评估队列执行器
     */
    public PredictionEvaluationQueueService(
            PredictionBackfillProperties backfillProperties,
            PriceRepository priceRepository,
            ModelPredictionBatchRepository batchRepository,
            ModelPredictionRepository predictionRepository,
            ModelPredictionQueryService queryService,
            PredictionEvaluationService evaluationService,
            MongoTemplate mongoTemplate,
            @Qualifier("modelEvaluationQueueExecutor") Executor queueExecutor) {
        this.backfillProperties = backfillProperties;
        this.priceRepository = priceRepository;
        this.batchRepository = batchRepository;
        this.predictionRepository = predictionRepository;
        this.queryService = queryService;
        this.evaluationService = evaluationService;
        this.mongoTemplate = mongoTemplate;
        this.queueExecutor = queueExecutor;
    }

    /**
     * 应用重启后将中断的评估批次恢复为排队状态。
     */
    public void recoverInterruptedBatches() {
        for (ModelPredictionBatchDocument batch
                : batchRepository.findByEvaluationQueueStatusOrderByTargetTradeDateAscCreatedAtAsc("RUNNING")) {
            batch.setEvaluationQueueStatus("QUEUED");
            batch.setEvaluationWaitReason(null);
            batch.setEvaluationStartedAt(null);
            batch.setEvaluationFinishedAt(null);
            batch.setEvaluationErrorMessage(null);
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            log.warn("恢复遗留评估队列批次: batchId={}, previousStatus=RUNNING", batch.getId());
        }
    }

    /**
     * 将指定或最新预测批次加入评估队列。
     *
     * @param request 评估请求
     * @return 评估入队结果
     */
    public PredictionOperationResponse evaluate(PredictionEvaluationRequest request) {
        ModelPredictionBatchDocument batch = queryService.resolveBatch(request == null ? null : request.getBatchId());
        boolean queued = enqueue(batch);
        submitWorker();
        return PredictionOperationResponse.builder()
                .batchId(batch.getId())
                .status(queued ? "QUEUED" : batch.getEvaluationQueueStatus())
                .message(queued ? "预测评估任务已入队" : "目标日行情尚未满足评估入队条件")
                .build();
    }

    /**
     * 扫描并唤醒已经满足行情条件的评估批次。
     *
     * @return 新进入评估队列的批次数量
     */
    public int resumeAvailable() {
        int queuedCount = enqueuePending();
        submitWorker();
        return queuedCount;
    }

    /**
     * 扫描所有可评估批次并按条件入队。
     *
     * @return 新入队批次数量
     */
    public int enqueuePending() {
        Query candidateQuery = Query.query(Criteria.where("status").in(EVALUATABLE_STATUSES)
                        .and("evaluationQueueStatus").nin("QUEUED", "RUNNING", "COMPLETED"))
                .with(Sort.by(Sort.Order.asc("targetTradeDate"), Sort.Order.asc("createdAt")));
        List<ModelPredictionBatchDocument> batches = mongoTemplate.find(
                candidateQuery, ModelPredictionBatchDocument.class);
        int queuedCount = 0;
        for (ModelPredictionBatchDocument batch : batches) {
            if (enqueue(batch)) {
                queuedCount++;
            }
        }
        return queuedCount;
    }

    /**
     * 按目标交易日行情覆盖情况尝试将批次加入评估队列。
     *
     * @param batch 预测批次
     * @return 是否本次成功入队
     */
    public boolean enqueue(ModelPredictionBatchDocument batch) {
        if ("QUEUED".equals(batch.getEvaluationQueueStatus())
                || "RUNNING".equals(batch.getEvaluationQueueStatus())) {
            return false;
        }
        if (!predictionRepository.existsByBatchIdAndEvaluationStatus(batch.getId(), "PENDING")) {
            if (!"COMPLETED".equals(batch.getEvaluationQueueStatus())) {
                batch.setEvaluationQueueStatus("COMPLETED");
                batch.setEvaluationWaitReason(null);
                batch.setEvaluationFinishedAt(LocalDateTime.now());
                batch.setEvaluationErrorMessage(null);
                batch.setUpdatedAt(LocalDateTime.now());
                batchRepository.save(batch);
            }
            return false;
        }
        LocalDate targetTradeDate = batch.getTargetTradeDate();
        if (targetTradeDate == null || targetTradeDate.isAfter(LocalDate.now(MARKET_ZONE))) {
            markWaiting(batch, WAIT_TARGET_DATE);
            return false;
        }
        List<String> stockCodes = predictionRepository.findByBatchId(batch.getId()).stream()
                .map(ModelPredictionDocument::getStockCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (stockCodes.isEmpty() || !hasRequiredPriceCoverage(stockCodes, targetTradeDate)) {
            markWaiting(batch, WAIT_MARKET_DATA);
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        Query query = Query.query(Criteria.where("_id").is(batch.getId())
                .and("evaluationQueueStatus").nin("QUEUED", "RUNNING"));
        Update update = new Update()
                .set("evaluationQueueStatus", "QUEUED")
                .unset("evaluationWaitReason")
                .set("evaluationQueuedAt", now)
                .set("evaluationStartedAt", null)
                .set("evaluationFinishedAt", null)
                .set("evaluationErrorMessage", null)
                .set("updatedAt", now);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ModelPredictionBatchDocument.class) != null;
    }

    /**
     * 清理评估失败批次的半成品并重新加入队列。
     *
     * @param batchId 预测批次标识
     * @return 重新入队结果
     */
    public synchronized PredictionOperationResponse retry(String batchId) {
        ModelPredictionBatchDocument batch = queryService.resolveBatch(batchId);
        if ("FAILED".equals(batch.getStatus())) {
            throw new IllegalStateException("预测任务失败，请先重新预测");
        }
        if (!hasStatus(batch, EVALUATABLE_STATUSES)) {
            throw new IllegalStateException("预测尚未完成，不能重新评估");
        }
        if (!"FAILED".equals(batch.getEvaluationQueueStatus())) {
            throw new IllegalStateException("仅评估失败的批次可以重新评估");
        }
        if (predictionRepository.countByBatchId(batch.getId()) <= 0) {
            throw new IllegalStateException("预测批次没有可重新评估的明细");
        }
        evaluationService.resetPredictionEvaluationDetails(batch.getId());
        if ("EVALUATED".equals(batch.getStatus())) {
            batch.setStatus(batch.getFailedCount() > 0 ? "PARTIAL" : "COMPLETED");
        }
        evaluationService.resetEvaluationSummary(batch, "评估失败批次已重新排队");
        batch.setEvaluationQueueStatus("QUEUED");
        batch.setEvaluationWaitReason(null);
        batch.setEvaluationQueuedAt(LocalDateTime.now());
        batch.setEvaluationStartedAt(null);
        batch.setEvaluationFinishedAt(null);
        batch.setEvaluationErrorMessage(null);
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        submitWorker();
        log.info("评估失败批次已重新入队: batchId={}", batch.getId());
        return PredictionOperationResponse.builder()
                .batchId(batch.getId()).status("QUEUED").message("评估失败批次已重新进入队列").build();
    }

    /**
     * 异步提交评估队列 Worker。
     *
     * @return Worker 完成信号
     */
    public CompletableFuture<Void> submitWorker() {
        return CompletableFuture.runAsync(this::consumeQueue, queueExecutor);
    }

    /**
     * 等待本次评估队列 Worker 完成并传播业务异常。
     *
     * @param worker Worker 完成信号
     */
    public void awaitWorker(CompletableFuture<Void> worker) {
        try {
            worker.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private void markWaiting(ModelPredictionBatchDocument batch, String waitReason) {
        if (!"WAITING".equals(batch.getEvaluationQueueStatus())
                || !Objects.equals(waitReason, batch.getEvaluationWaitReason())) {
            batch.setEvaluationQueueStatus("WAITING");
            batch.setEvaluationWaitReason(waitReason);
            batch.setEvaluationErrorMessage(null);
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
        }
    }

    private void consumeQueue() {
        if (!running.compareAndSet(false, true)) {
            log.info("评估队列已有 Worker 正在消费，本次触发仅保留持久化队列数据");
            return;
        }
        try {
            ModelPredictionBatchDocument batch;
            while ((batch = claimNext()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    batch.setEvaluationQueueStatus("QUEUED");
                    batch.setEvaluationWaitReason(null);
                    batch.setEvaluationErrorMessage(null);
                    batch.setUpdatedAt(LocalDateTime.now());
                    batchRepository.save(batch);
                    throw new IllegalStateException("评估队列消费已被中断");
                }
                executeBatch(batch);
            }
        } finally {
            running.set(false);
            if (batchRepository.existsByEvaluationQueueStatus("QUEUED")) {
                submitWorker();
            }
        }
    }

    private ModelPredictionBatchDocument claimNext() {
        Query query = Query.query(Criteria.where("evaluationQueueStatus").is("QUEUED"))
                .with(Sort.by(Sort.Order.asc("targetTradeDate"), Sort.Order.asc("createdAt")));
        Update update = new Update()
                .set("evaluationQueueStatus", "RUNNING")
                .unset("evaluationWaitReason")
                .set("evaluationStartedAt", LocalDateTime.now())
                .set("evaluationErrorMessage", null)
                .set("updatedAt", LocalDateTime.now());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ModelPredictionBatchDocument.class);
    }

    private void executeBatch(ModelPredictionBatchDocument batch) {
        try {
            batch.setEvaluationQueueStatus("RUNNING");
            batch.setEvaluationWaitReason(null);
            batch.setEvaluationStartedAt(LocalDateTime.now());
            batch.setEvaluationErrorMessage(null);
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            evaluationService.evaluateBatch(batch);
            ModelPredictionBatchDocument refreshed = batchRepository.findById(batch.getId()).orElseThrow();
            boolean pending = predictionRepository.existsByBatchIdAndEvaluationStatus(batch.getId(), "PENDING");
            refreshed.setEvaluationQueueStatus(pending ? "WAITING" : "COMPLETED");
            refreshed.setEvaluationWaitReason(pending ? WAIT_PENDING_DETAILS : null);
            refreshed.setEvaluationFinishedAt(LocalDateTime.now());
            refreshed.setEvaluationErrorMessage(null);
            refreshed.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(refreshed);
        } catch (RuntimeException exception) {
            ModelPredictionBatchDocument failed = batchRepository.findById(batch.getId()).orElse(batch);
            failed.setEvaluationQueueStatus("FAILED");
            failed.setEvaluationWaitReason(null);
            failed.setEvaluationFinishedAt(LocalDateTime.now());
            failed.setEvaluationErrorMessage(exception.getMessage());
            failed.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(failed);
            log.warn("预测评估队列批次执行失败: batchId={}, message={}",
                    batch.getId(), exception.getMessage(), exception);
        }
    }

    private boolean hasRequiredPriceCoverage(List<String> stockCodes, LocalDate tradeDate) {
        long priceCount = priceRepository.countByCodeInAndDate(stockCodes, tradeDate);
        double coverageRate = stockCodes.isEmpty() ? 0D : priceCount / (double) stockCodes.size();
        return coverageRate >= backfillProperties.resolveMinPriceCoverageRate();
    }

    private boolean hasStatus(ModelPredictionBatchDocument batch, List<String> statuses) {
        return batch != null && batch.getStatus() != null && statuses.contains(batch.getStatus());
    }
}
// AI_GENERATE_END--
