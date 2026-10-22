// AI_GENERATE_START------------ -------------
package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.modelService.config.PredictionBackfillProperties;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.dto.ModelPredictionAnalysisDto;
import com.stock.modelService.domain.dto.PredictionBackfillResponse;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.entity.ModelActivationDocument;
import com.stock.modelService.domain.entity.ModelPredictionBatchDocument;
import com.stock.modelService.domain.entity.ModelPredictionDocument;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.persistence.ModelActivationRepository;
import com.stock.modelService.persistence.ModelPredictionBatchRepository;
import com.stock.modelService.persistence.ModelPredictionRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 全市场模型预测、实际结果回填和能力评估服务。
 * 将已激活模型的预测固化到 MongoDB，并在目标交易日行情到达后形成可审计评估闭环。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Slf4j
@Service
public class ModelPredictionAnalysisService {

    private static final int PREDICTION_BATCH_SIZE = 100;
    private static final int MINIMUM_ABILITY_SAMPLE_COUNT = 100;
    private static final int MINIMUM_CALIBRATION_SAMPLE_COUNT = 100;
    private static final double MIN_DIRECTION_PROBABILITY_STD = 0.005D;
    private static final double MIN_PREDICTED_RETURN_STD = 0.0005D;
    private static final double MIN_BASELINE_ACCURACY_LIFT = 0.02D;
    private static final int ROLLING_WINDOW_BATCHES = 20;
    private static final int MINIMUM_ROLLING_SAMPLE_COUNT = 5000;
    private static final List<String> RUNNING_STATUSES = List.of("QUEUED", "RUNNING");
    private static final List<String> EVALUATABLE_STATUSES = List.of("COMPLETED", "PARTIAL", "EVALUATED");
    private static final List<String> PRESENT_PREDICTION_STATUSES =
            List.of("QUEUED", "RUNNING", "COMPLETED", "PARTIAL", "EVALUATED");
    private static final String LEGACY_PREDICTION_IDENTITY_INDEX = "uk_prediction_identity";
    private static final String BATCH_STOCK_UNIQUE_INDEX = "uk_prediction_batch_stock";
    private static final String HISTORICAL_BACKFILL_TRIGGER = "scheduler-backfill";
    private static final String MANUAL_BACKFILL_TRIGGER_PREFIX = "manual-backfill:";
    private static final String EVALUATION_WAIT_PREDICTION = "PREDICTION_NOT_COMPLETED";
    private static final String EVALUATION_WAIT_TARGET_DATE = "TARGET_DATE_NOT_REACHED";
    private static final String EVALUATION_WAIT_MARKET_DATA = "MARKET_DATA_COVERAGE_LOW";
    private static final String EVALUATION_WAIT_PENDING_DETAILS = "PENDING_MARKET_DATA";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final LstmInferenceService inferenceService;
    private final LstmTrainerService trainerService;
    private final PredictionBackfillProperties predictionBackfillProperties;
    private final StockInfoRepository stockInfoRepository;
    private final PriceRepository priceRepository;
    private final TradingCalendarService tradingCalendarService;
    private final ModelActivationRepository activationRepository;
    private final LstmModelRepository modelRepository;
    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;
    private final MongoTemplate mongoTemplate;
    private final Executor predictionQueueExecutor;
    private final Executor evaluationQueueExecutor;
    private final AtomicBoolean backfillScanRunning = new AtomicBoolean(false);
    private final AtomicBoolean predictionRunning = new AtomicBoolean(false);
    private final AtomicBoolean evaluationRunning = new AtomicBoolean(false);

    /**
     * 创建模型预测分析服务。
     *
     * @param inferenceService LSTM 推理服务
     * @param trainerService LSTM 训练与模型元数据服务
     * @param predictionBackfillProperties 历史预测补缺配置
     * @param stockInfoRepository 股票基础信息仓库
     * @param priceRepository 股票日线仓库
     * @param tradingCalendarService 可靠交易日解析服务
     * @param activationRepository 模型激活指针仓库
     * @param modelRepository 模型版本仓库
     * @param batchRepository 预测批次仓库
     * @param predictionRepository 预测明细仓库
     * @param mongoTemplate MongoDB 操作模板
     * @param predictionQueueExecutor 模型预测队列执行器
     * @param evaluationQueueExecutor 模型评估队列执行器
     */
    public ModelPredictionAnalysisService(
            LstmInferenceService inferenceService,
            LstmTrainerService trainerService,
            PredictionBackfillProperties predictionBackfillProperties,
            StockInfoRepository stockInfoRepository,
            PriceRepository priceRepository,
            TradingCalendarService tradingCalendarService,
            ModelActivationRepository activationRepository,
            LstmModelRepository modelRepository,
            ModelPredictionBatchRepository batchRepository,
            ModelPredictionRepository predictionRepository,
            MongoTemplate mongoTemplate,
            @Qualifier("modelPredictionQueueExecutor") Executor predictionQueueExecutor,
            @Qualifier("modelEvaluationQueueExecutor") Executor evaluationQueueExecutor) {
        this.inferenceService = inferenceService;
        this.trainerService = trainerService;
        this.predictionBackfillProperties = predictionBackfillProperties;
        this.stockInfoRepository = stockInfoRepository;
        this.priceRepository = priceRepository;
        this.tradingCalendarService = tradingCalendarService;
        this.activationRepository = activationRepository;
        this.modelRepository = modelRepository;
        this.batchRepository = batchRepository;
        this.predictionRepository = predictionRepository;
        this.mongoTemplate = mongoTemplate;
        this.predictionQueueExecutor = predictionQueueExecutor;
        this.evaluationQueueExecutor = evaluationQueueExecutor;
    }

    /**
     * 应用重启后恢复遗留的预测和评估队列状态。
     *
     * 异步线程不会跨进程继续运行，运行中的任务恢复为排队状态，等待统一 Worker 再次消费。
     */
    @PostConstruct
    public void recoverInterruptedPredictionBatches() {
        reconcilePredictionIndexes();
        for (ModelPredictionBatchDocument batch : batchRepository.findByStatusIn(List.of("RUNNING"))) {
            batch.setStatus("QUEUED");
            batch.setErrorMessage(null);
            batch.setProgressMessage("应用重启，预测任务已恢复排队");
            batch.setStartedAt(null);
            batch.setFinishedAt(null);
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            log.warn("恢复遗留预测队列批次: batchId={}, previousStatus=RUNNING", batch.getId());
        }
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
        for (ModelPredictionBatchDocument batch : batchRepository.findByCalendarFallbackIsNull()) {
            if (batch.getBaseTradeDate() == null) {
                continue;
            }
            try {
                TradeDateResolution resolution = resolveNextTradeDate(batch.getBaseTradeDate());
                batch.setCalendarFallback(resolution.calendarFallback());
                if (batch.getInformationCutoffAt() == null) {
                    batch.setInformationCutoffAt(batch.getCreatedAt());
                }
                batch.setUpdatedAt(LocalDateTime.now());
                batchRepository.save(batch);
                log.info("已回填历史预测批次日历来源: batchId={}, calendarFallback={}",
                        batch.getId(), resolution.calendarFallback());
            } catch (IllegalStateException exception) {
                log.warn("历史预测批次暂无法绑定已确认交易日历: batchId={}, baseTradeDate={}, reason={}",
                        batch.getId(), batch.getBaseTradeDate(), exception.getMessage());
            }
        }
    }

    private void reconcilePredictionIndexes() {
        IndexOperations indexOperations = mongoTemplate.indexOps(ModelPredictionDocument.class);
        boolean legacyIndexExists = indexOperations.getIndexInfo().stream()
                .anyMatch(indexInfo -> LEGACY_PREDICTION_IDENTITY_INDEX.equals(indexInfo.getName()));
        if (legacyIndexExists) {
            indexOperations.dropIndex(LEGACY_PREDICTION_IDENTITY_INDEX);
            log.warn("已删除跨批次冲突的旧预测唯一索引: index={}", LEGACY_PREDICTION_IDENTITY_INDEX);
        }
        indexOperations.ensureIndex(new Index()
                .on("batchId", Sort.Direction.ASC)
                .on("stockCode", Sort.Direction.ASC)
                .unique()
                .named(BATCH_STOCK_UNIQUE_INDEX));
    }

    /**
     * 使用原批次绑定的模型版本和历史基准日重新生成预测。
     * 原批次及其评估结果保持不变，重算结果会落入新的预测批次。
     *
     * @param request 历史预测重算请求
     * @return 新建或复用的历史预测批次信息
     */
    public ModelPredictionAnalysisDto.OperationResponse rerunPrediction(
            ModelPredictionAnalysisDto.RerunPredictionRequest request) {
        String sourceBatchId = request == null ? null : request.getBatchId();
        ModelPredictionBatchDocument source = resolveBatch(sourceBatchId);
        ModelPredictionBatchDocument existingRerun = batchRepository
                .findFirstByRerunSourceBatchIdOrderByCreatedAtDesc(source.getId());
        if (existingRerun != null) {
            return ModelPredictionAnalysisDto.OperationResponse.builder()
                    .batchId(existingRerun.getId()).status(existingRerun.getStatus())
                    .message("该历史批次已存在重算结果，已返回原重算批次").build();
        }
        if (source.getBaseTradeDate() == null || source.getModelId() == null) {
            throw new IllegalStateException("原预测批次缺少模型版本或基准交易日，无法重算");
        }
        LstmModelDocument model = modelRepository.findById(source.getModelId())
                .orElseThrow(() -> new IllegalStateException("原预测批次绑定的模型版本不存在"));
        trainerService.validateModelVersion(model.getId());
        List<String> stockCodes = predictionRepository.findByBatchId(source.getId()).stream()
                .map(ModelPredictionDocument::getStockCode).filter(code -> code != null && !code.isBlank())
                .distinct().sorted().toList();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("原预测批次没有可重算的股票明细");
        }
        LocalDateTime now = LocalDateTime.now();
        ModelPredictionBatchDocument rerun = new ModelPredictionBatchDocument();
        rerun.setModelName(source.getModelName());
        rerun.setModelId(model.getId());
        rerun.setRerunSourceBatchId(source.getId());
        rerun.setModelVersion(model.getModelVersion());
        rerun.setBaseTradeDate(source.getBaseTradeDate());
        rerun.setTargetTradeDate(source.getTargetTradeDate());
        rerun.setInformationCutoffAt(source.getInformationCutoffAt());
        rerun.setCalendarFallback(source.getCalendarFallback());
        rerun.setStatus("QUEUED");
        rerun.setTotalStocks(stockCodes.size());
        rerun.setAbilityLevel("INSUFFICIENT_DATA");
        rerun.setAbilityMessage("历史预测重算尚未完成");
        rerun.setProgressMessage("历史预测重算任务已排队");
        rerun.setEvaluationQueueStatus("WAITING");
        rerun.setEvaluationWaitReason(EVALUATION_WAIT_PREDICTION);
        rerun.setTriggeredBy(normalizeOperator(request == null ? null : request.getOperatorName()));
        rerun.setCreatedAt(now);
        rerun.setUpdatedAt(now);
        ModelPredictionBatchDocument saved;
        try {
            saved = batchRepository.save(rerun);
        } catch (DuplicateKeyException duplicateKeyException) {
            ModelPredictionBatchDocument concurrent = batchRepository
                    .findFirstByRerunSourceBatchIdOrderByCreatedAtDesc(source.getId());
            if (concurrent == null) {
                throw duplicateKeyException;
            }
            return ModelPredictionAnalysisDto.OperationResponse.builder()
                    .batchId(concurrent.getId()).status(concurrent.getStatus())
                    .message("该历史批次已存在重算结果，已返回原重算批次").build();
        }
        submitPredictionQueueWorker();
        return ModelPredictionAnalysisDto.OperationResponse.builder()
                .batchId(saved.getId()).status(saved.getStatus()).message("历史预测重算任务已提交").build();
    }
    /**
     * 扫描并流水线消费每日预测和评估队列。
     *
     * 统一任务补齐两个持久化队列后，允许历史评估与后续预测跨批次并行执行；
     * 同一批次仍由状态门禁保证预测完成后才能进入评估。
     *
     * @return 扫描、预测入队和评估入队汇总结果
     * @throws IllegalStateException 模型、行情或交易日历不满足执行条件时抛出
     */
    public PredictionBackfillResponse startScheduledPrediction() {
        PredictionBackfillResponse response;
        RuntimeException predictionFailure = null;
        int evaluationQueuedCount = 0;
        try {
            response = scanAndQueuePredictionBackfill("scheduler");
            evaluationQueuedCount += enqueuePendingEvaluations();
            submitEvaluationQueueWorker();
            awaitQueueWorker(submitPredictionQueueWorker());
        } catch (RuntimeException exception) {
            predictionFailure = exception;
            response = buildBackfillResponse(0, 0, 0, 0, 0, null);
            log.error("统一任务预测阶段失败，继续执行历史评估阶段: reason={}", exception.getMessage(), exception);
        }

        try {
            evaluationQueuedCount += enqueuePendingEvaluations();
            awaitQueueWorker(submitEvaluationQueueWorker());
        } catch (RuntimeException evaluationFailure) {
            if (predictionFailure != null) {
                evaluationFailure.addSuppressed(predictionFailure);
            }
            throw evaluationFailure;
        }
        refreshBackfillResponse(response, evaluationQueuedCount);
        if (predictionFailure != null) {
            throw new IllegalStateException("预测补缺阶段失败，历史评估阶段已继续执行: "
                    + predictionFailure.getMessage(), predictionFailure);
        }
        return response;
    }

    private PredictionBackfillResponse scanAndQueuePredictionBackfill(String triggeredBy) {
        if (!backfillScanRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("预测和评估缺口扫描正在执行，请稍后重试");
        }
        try {
            return doScanAndQueuePredictionBackfill(triggeredBy);
        } finally {
            backfillScanRunning.set(false);
        }
    }

    private PredictionBackfillResponse doScanAndQueuePredictionBackfill(String triggeredBy) {
        int scanTradingDays = predictionBackfillProperties.resolveHistoryBackfillTradingDays();
        if (scanTradingDays == 0) {
            return buildBackfillResponse(0, 0, 0, 0, 0, null);
        }
        PredictionContext context = resolvePredictionContext();
        List<String> stockCodes = loadPredictionStockCodes();
        LocalDate today = LocalDate.now(MARKET_ZONE);
        LocalDate targetTradeDate = resolveScanStartTradeDate(today);
        int existingPredictionCount = 0;
        int predictionQueuedCount = 0;
        int waitingForMarketDataCount = 0;
        String firstQueuedBatchId = null;
        for (int index = 0; index < scanTradingDays; index++) {
            ModelPredictionBatchDocument existing = batchRepository
                    .findFirstByTargetTradeDateAndStatusInOrderByCreatedAtDesc(
                            targetTradeDate, PRESENT_PREDICTION_STATUSES);
            if (hasStatus(existing, RUNNING_STATUSES)
                    || isReusablePredictionBatch(existing)) {
                existingPredictionCount++;
            } else {
                LocalDate baseTradeDate = tradingCalendarService.previousTradingDay(targetTradeDate);
                if (!hasRequiredPriceCoverage(stockCodes, baseTradeDate)) {
                    waitingForMarketDataCount++;
                    log.warn("预测缺口暂不入队，基准日行情覆盖率不足: baseTradeDate={}, targetTradeDate={}",
                            baseTradeDate, targetTradeDate);
                } else {
                    String batchTriggeredBy = triggeredBy;
                    if ("scheduler".equals(triggeredBy) && !targetTradeDate.equals(today)) {
                        batchTriggeredBy = HISTORICAL_BACKFILL_TRIGGER;
                    }
                    PredictionBatchPreparation preparation = preparePredictionBatch(
                            context, baseTradeDate, targetTradeDate, stockCodes, batchTriggeredBy);
                    if (preparation.created()) {
                        predictionQueuedCount++;
                        if (firstQueuedBatchId == null) {
                            firstQueuedBatchId = preparation.batch().getId();
                        }
                    } else {
                        existingPredictionCount++;
                    }
                }
            }
            targetTradeDate = tradingCalendarService.previousTradingDay(targetTradeDate);
        }
        return buildBackfillResponse(scanTradingDays, existingPredictionCount, predictionQueuedCount,
                waitingForMarketDataCount, 0, firstQueuedBatchId);
    }

    private PredictionBackfillResponse buildBackfillResponse(
            int scannedTradingDays, int existingPredictionCount, int predictionQueuedCount,
            int waitingForMarketDataCount, int evaluationQueuedCount, String firstQueuedBatchId) {
        String message = "已扫描 " + scannedTradingDays + " 个交易日，预测入队 " + predictionQueuedCount
                + " 个，评估入队 " + evaluationQueuedCount + " 个，行情待补 "
                + waitingForMarketDataCount + " 个";
        return PredictionBackfillResponse.builder()
                .scannedTradingDays(scannedTradingDays)
                .existingPredictionCount(existingPredictionCount)
                .predictionQueuedCount(predictionQueuedCount)
                .predictionWaitingForMarketDataCount(waitingForMarketDataCount)
                .evaluationQueuedCount(evaluationQueuedCount)
                .batchId(firstQueuedBatchId)
                .status(predictionQueuedCount > 0 || evaluationQueuedCount > 0 ? "QUEUED" : "NO_CHANGE")
                .message(message)
                .build();
    }

    private void refreshBackfillResponse(PredictionBackfillResponse response, int evaluationQueuedCount) {
        response.setEvaluationQueuedCount(evaluationQueuedCount);
        response.setStatus(response.getPredictionQueuedCount() > 0 || evaluationQueuedCount > 0
                ? "QUEUED" : "NO_CHANGE");
        response.setMessage("已扫描 " + response.getScannedTradingDays() + " 个交易日，预测入队 "
                + response.getPredictionQueuedCount() + " 个，评估入队 " + evaluationQueuedCount
                + " 个，行情待补 " + response.getPredictionWaitingForMarketDataCount() + " 个");
    }

    private LocalDate resolveScanStartTradeDate(LocalDate today) {
        if (!tradingCalendarService.isConfirmedDate(today)) {
            throw new IllegalStateException("当日交易日历缺失或尚未确认: " + today);
        }
        return tradingCalendarService.isTradingDay(today)
                ? today : tradingCalendarService.previousTradingDay(today);
    }

    private boolean hasRequiredPriceCoverage(List<String> stockCodes, LocalDate tradeDate) {
        long priceCount = priceRepository.countByCodeInAndDate(stockCodes, tradeDate);
        double coverageRate = rate((int) Math.min(priceCount, Integer.MAX_VALUE), stockCodes.size());
        return coverageRate >= predictionBackfillProperties.resolveMinPriceCoverageRate();
    }

    /**
     * 回填指定批次或最新批次的实际行情并刷新能力指标。
     *
     * @param request 评估请求
     * @return 评估后的批次状态
     */
    public ModelPredictionAnalysisDto.OperationResponse evaluatePredictions(
            ModelPredictionAnalysisDto.EvaluatePredictionRequest request) {
        ModelPredictionBatchDocument batch = resolveBatch(request == null ? null : request.getBatchId());
        boolean queued = enqueueEvaluationBatch(batch);
        submitEvaluationQueueWorker();
        return ModelPredictionAnalysisDto.OperationResponse.builder()
                .batchId(batch.getId())
                .status(queued ? "QUEUED" : batch.getEvaluationQueueStatus())
                .message(queued ? "预测评估任务已入队" : "目标日行情尚未满足评估入队条件")
                .build();
    }

    /**
     * 在行情同步完成后扫描并唤醒已满足条件的评估批次。
     *
     * @return 本次新进入评估队列的批次数量
     */
    public int resumeAvailableEvaluations() {
        int queuedCount = enqueuePendingEvaluations();
        submitEvaluationQueueWorker();
        return queuedCount;
    }

    private int enqueuePendingEvaluations() {
        Query candidateQuery = Query.query(Criteria.where("status").in(EVALUATABLE_STATUSES)
                        .and("evaluationQueueStatus").nin("QUEUED", "RUNNING", "COMPLETED"))
                .with(Sort.by(Sort.Order.asc("targetTradeDate"), Sort.Order.asc("createdAt")));
        List<ModelPredictionBatchDocument> batches = mongoTemplate.find(
                candidateQuery, ModelPredictionBatchDocument.class);
        int queuedCount = 0;
        for (ModelPredictionBatchDocument batch : batches) {
            if (enqueueEvaluationBatch(batch)) {
                queuedCount++;
            }
        }
        return queuedCount;
    }

    private boolean enqueueEvaluationBatch(ModelPredictionBatchDocument batch) {
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
            markEvaluationWaiting(batch, EVALUATION_WAIT_TARGET_DATE);
            return false;
        }
        List<String> stockCodes = predictionRepository.findByBatchId(batch.getId()).stream()
                .map(ModelPredictionDocument::getStockCode)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (stockCodes.isEmpty() || !hasRequiredPriceCoverage(stockCodes, targetTradeDate)) {
            markEvaluationWaiting(batch, EVALUATION_WAIT_MARKET_DATA);
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

    private void markEvaluationWaiting(ModelPredictionBatchDocument batch, String waitReason) {
        if (!"WAITING".equals(batch.getEvaluationQueueStatus())
                || !Objects.equals(waitReason, batch.getEvaluationWaitReason())) {
            batch.setEvaluationQueueStatus("WAITING");
            batch.setEvaluationWaitReason(waitReason);
            batch.setEvaluationErrorMessage(null);
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
        }
    }

    private ModelPredictionBatchDocument evaluateBatch(ModelPredictionBatchDocument batch) {
        if (hasStatus(batch, RUNNING_STATUSES)) {
            throw new IllegalStateException("预测批次尚未完成，暂不能评估");
        }
        List<ModelPredictionDocument> predictions = predictionRepository.findByBatchId(batch.getId());
        if (predictions.isEmpty()) {
            throw new IllegalStateException("预测批次没有可评估明细");
        }

        LocalDate today = LocalDate.now(MARKET_ZONE);
        LocalDate latestMarketDate = priceRepository.findTopByOrderByDateDesc()
                .map(StockPrice::getDate)
                .orElse(null);
        Map<LocalDate, List<ModelPredictionDocument>> byTargetDate = predictions.stream()
                .filter(item -> item.getTargetTradeDate() != null)
                .collect(Collectors.groupingBy(ModelPredictionDocument::getTargetTradeDate));
        LocalDateTime evaluatedAt = LocalDateTime.now();
        List<ModelPredictionDocument> changed = new ArrayList<>();
        int missingMarketDataCount = 0;
        for (Map.Entry<LocalDate, List<ModelPredictionDocument>> entry : byTargetDate.entrySet()) {
            if (entry.getKey().isAfter(today)) {
                log.info("跳过尚未到达目标交易日的预测评估: batchId={}, targetTradeDate={}, today={}",
                        batch.getId(), entry.getKey(), today);
                continue;
            }
            List<String> codes = entry.getValue().stream().map(ModelPredictionDocument::getStockCode).toList();
            Map<String, StockPrice> actualPrices = priceRepository.findByCodeInAndDate(codes, entry.getKey()).stream()
                    .collect(Collectors.toMap(StockPrice::getCode, Function.identity(), (left, right) -> left));
            for (ModelPredictionDocument prediction : entry.getValue()) {
                StockPrice actualPrice = actualPrices.get(prediction.getStockCode());
                if (actualPrice != null && isPositive(actualPrice.getClosePrice())
                        && prediction.getBaseClosePrice() != null && prediction.getBaseClosePrice() > 0D) {
                    double actualClose = actualPrice.getClosePrice().doubleValue();
                    double actualReturn = actualClose / prediction.getBaseClosePrice() - 1D;
                    double error = prediction.getPredictedReturn() - actualReturn;
                    prediction.setActualClosePrice(actualClose);
                    prediction.setActualReturn(actualReturn);
                    prediction.setReturnError(error);
                    prediction.setAbsoluteError(Math.abs(error));
                    prediction.setSquaredError(error * error);
                    boolean predictedUp = prediction.getDirectionProbability() != null
                            && prediction.getDirectionProbability() >= 0.5D;
                    prediction.setDirectionCorrect(predictedUp == (actualReturn > 0D));
                    prediction.setEvaluationStatus("EVALUATED");
                    prediction.setEvaluatedAt(evaluatedAt);
                    changed.add(prediction);
                } else {
                    missingMarketDataCount++;
                }
            }
        }
        if (!changed.isEmpty()) {
            predictionRepository.saveAll(changed);
        }
        refreshAbility(batch, predictionRepository.findByBatchId(batch.getId()));
        ModelPredictionBatchDocument refreshed = batchRepository.findById(batch.getId()).orElseThrow();
        log.info("预测实际结果刷新完成: batchId={}, targetTradeDate={}, today={}, latestMarketDate={}, "
                        + "evaluated={}, pendingMarketData={}, noMarketData={}, ability={}",
                refreshed.getId(), batch.getTargetTradeDate(), today, latestMarketDate,
                refreshed.getEvaluatedCount(), missingMarketDataCount,
                refreshed.getNoMarketDataCount(), refreshed.getAbilityLevel());
        if (missingMarketDataCount > 0) {
            log.warn("预测批次仍有明细等待目标日行情: batchId={}, missingMarketData={}, latestMarketDate={}",
                    refreshed.getId(), missingMarketDataCount, latestMarketDate);
        }
        return refreshed;
    }

    /**
     * 分页查询预测批次。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 预测批次分页响应
     */
    public ModelPredictionAnalysisDto.BatchPageResponse listBatches(int current, int pageSize) {
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Sort predictionWindowSort = Sort.by(
                Sort.Order.desc("targetTradeDate"),
                Sort.Order.desc("baseTradeDate"));
        Page<ModelPredictionBatchDocument> page = batchRepository.findAll(PageRequest.of(
                safeCurrent - 1, safePageSize, predictionWindowSort));
        return ModelPredictionAnalysisDto.BatchPageResponse.builder()
                .items(page.getContent().stream().map(this::toBatchItem).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build();
    }

    /**
     * 清理预测失败批次的残留结果，并将原批次重新加入预测队列。
     *
     * @param batchId 预测失败的批次 ID
     * @return 重新入队后的批次状态
     * @throws IllegalStateException 批次不是预测失败状态时抛出
     */
    public synchronized ModelPredictionAnalysisDto.OperationResponse retryPrediction(String batchId) {
        ModelPredictionBatchDocument batch = resolveBatch(batchId);
        if (!"FAILED".equals(batch.getStatus())) {
            throw new IllegalStateException("仅预测失败的批次可以重新预测");
        }

        predictionRepository.deleteByBatchId(batch.getId());
        batch.setStatus("QUEUED");
        batch.setProcessedStocks(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setCoverageRate(null);
        batch.setStartedAt(null);
        batch.setFinishedAt(null);
        batch.setErrorMessage(null);
        batch.setProgressMessage("预测失败批次已重新排队");
        batch.setEvaluationQueueStatus("WAITING");
        batch.setEvaluationWaitReason(EVALUATION_WAIT_PREDICTION);
        batch.setEvaluationQueuedAt(null);
        batch.setEvaluationStartedAt(null);
        batch.setEvaluationFinishedAt(null);
        batch.setEvaluationErrorMessage(null);
        resetEvaluationSummary(batch, "预测重试尚未完成");
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        submitPredictionQueueWorker();
        log.info("预测失败批次已重新入队: batchId={}", batch.getId());
        return ModelPredictionAnalysisDto.OperationResponse.builder()
                .batchId(batch.getId())
                .status("QUEUED")
                .message("预测失败批次已重新进入队列")
                .build();
    }

    /**
     * 清理评估失败批次的半成品评估结果，并将原批次重新加入评估队列。
     *
     * @param batchId 评估失败的批次 ID
     * @return 重新入队后的评估状态
     * @throws IllegalStateException 批次不是可重试的评估失败状态时抛出
     */
    public synchronized ModelPredictionAnalysisDto.OperationResponse retryEvaluation(String batchId) {
        ModelPredictionBatchDocument batch = resolveBatch(batchId);
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

        resetPredictionEvaluationDetails(batch.getId());
        if ("EVALUATED".equals(batch.getStatus())) {
            batch.setStatus(batch.getFailedCount() > 0 ? "PARTIAL" : "COMPLETED");
        }
        resetEvaluationSummary(batch, "评估失败批次已重新排队");
        batch.setEvaluationQueueStatus("QUEUED");
        batch.setEvaluationWaitReason(null);
        batch.setEvaluationQueuedAt(LocalDateTime.now());
        batch.setEvaluationStartedAt(null);
        batch.setEvaluationFinishedAt(null);
        batch.setEvaluationErrorMessage(null);
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        submitEvaluationQueueWorker();
        log.info("评估失败批次已重新入队: batchId={}", batch.getId());
        return ModelPredictionAnalysisDto.OperationResponse.builder()
                .batchId(batch.getId())
                .status("QUEUED")
                .message("评估失败批次已重新进入队列")
                .build();
    }

    /**
     * 分页查询预测值与实际值对比。
     *
     * @param batchId 预测批次 ID；为空时查询最新批次
     * @param evaluationStatus 评估状态筛选
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 预测明细分页响应
     */
    public ModelPredictionAnalysisDto.PredictionPageResponse listPredictions(
            String batchId, String evaluationStatus, int current, int pageSize) {
        ModelPredictionBatchDocument batch = resolveBatch(batchId);
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.max(1, Math.min(200, pageSize));
        PageRequest pageable = PageRequest.of(safeCurrent - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "predictedReturn"));
        Page<ModelPredictionDocument> page = evaluationStatus == null || evaluationStatus.isBlank()
                ? predictionRepository.findByBatchId(batch.getId(), pageable)
                : predictionRepository.findByBatchIdAndEvaluationStatus(
                        batch.getId(), evaluationStatus.trim().toUpperCase(), pageable);
        return ModelPredictionAnalysisDto.PredictionPageResponse.builder()
                .items(page.getContent().stream().map(this::toPredictionItem).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build();
    }

    /**
     * 查询指定或最新预测批次的模型能力概要。
     *
     * @param batchId 预测批次 ID；为空时查询最新批次
     * @return 模型能力概要
     */
    public ModelPredictionAnalysisDto.AbilityResponse getAbility(String batchId) {
        return toAbility(resolveBatch(batchId));
    }

    private CompletableFuture<Void> submitPredictionQueueWorker() {
        return CompletableFuture.runAsync(this::consumePredictionQueue, predictionQueueExecutor);
    }

    private CompletableFuture<Void> submitEvaluationQueueWorker() {
        return CompletableFuture.runAsync(this::consumeEvaluationQueue, evaluationQueueExecutor);
    }

    private void awaitQueueWorker(CompletableFuture<Void> worker) {
        try {
            worker.join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private void consumePredictionQueue() {
        if (!predictionRunning.compareAndSet(false, true)) {
            log.info("预测队列已有 Worker 正在消费，本次触发仅保留持久化队列数据");
            return;
        }
        try {
            ModelPredictionBatchDocument batch;
            while ((batch = claimNextPredictionBatch()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    batch.setStatus("QUEUED");
                    batch.setProgressMessage("预测队列消费中断，任务已重新排队");
                    batch.setUpdatedAt(LocalDateTime.now());
                    batchRepository.save(batch);
                    throw new IllegalStateException("预测队列消费已被中断");
                }
                try {
                    List<String> stockCodes = resolveQueuedBatchStockCodes(batch);
                    if (batch.getProcessedStocks() > 0
                            || predictionRepository.countByBatchId(batch.getId()) > 0) {
                        predictionRepository.deleteByBatchId(batch.getId());
                        batch.setProcessedStocks(0);
                        batch.setSuccessCount(0);
                        batch.setFailedCount(0);
                    }
                    batch.setTotalStocks(stockCodes.size());
                    batch.setErrorMessage(null);
                    batch.setUpdatedAt(LocalDateTime.now());
                    batchRepository.save(batch);
                    executePrediction(batch.getId(), stockCodes);
                } catch (RuntimeException exception) {
                    ModelPredictionBatchDocument failed = batchRepository.findById(batch.getId()).orElse(batch);
                    failed.setStatus("FAILED");
                    failed.setErrorMessage(exception.getMessage());
                    failed.setProgressMessage("预测队列任务执行失败");
                    failed.setEvaluationQueueStatus("FAILED");
                    failed.setEvaluationWaitReason(null);
                    failed.setEvaluationErrorMessage("预测任务失败，无法进入评估队列");
                    failed.setFinishedAt(LocalDateTime.now());
                    failed.setUpdatedAt(LocalDateTime.now());
                    batchRepository.save(failed);
                    log.error("预测队列批次执行失败: batchId={}", batch.getId(), exception);
                }
            }
        } finally {
            predictionRunning.set(false);
            if (batchRepository.existsByStatus("QUEUED")) {
                submitPredictionQueueWorker();
            }
        }
    }

    private ModelPredictionBatchDocument claimNextPredictionBatch() {
        Query query = Query.query(Criteria.where("status").is("QUEUED"))
                .with(Sort.by(Sort.Order.asc("targetTradeDate"), Sort.Order.asc("createdAt")));
        Update update = new Update()
                .set("status", "RUNNING")
                .set("startedAt", LocalDateTime.now())
                .set("progressMessage", "正在执行全市场预测")
                .set("updatedAt", LocalDateTime.now());
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ModelPredictionBatchDocument.class);
    }

    private List<String> resolveQueuedBatchStockCodes(ModelPredictionBatchDocument batch) {
        if (batch.getRerunSourceBatchId() == null || batch.getRerunSourceBatchId().isBlank()) {
            return loadPredictionStockCodes();
        }
        List<String> stockCodes = predictionRepository.findByBatchId(batch.getRerunSourceBatchId()).stream()
                .map(ModelPredictionDocument::getStockCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .sorted()
                .toList();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("历史预测重算来源批次没有可用股票明细: " + batch.getRerunSourceBatchId());
        }
        return stockCodes;
    }

    private void consumeEvaluationQueue() {
        if (!evaluationRunning.compareAndSet(false, true)) {
            log.info("评估队列已有 Worker 正在消费，本次触发仅保留持久化队列数据");
            return;
        }
        try {
            ModelPredictionBatchDocument batch;
            while ((batch = claimNextEvaluationBatch()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    batch.setEvaluationQueueStatus("QUEUED");
                    batch.setEvaluationWaitReason(null);
                    batch.setEvaluationErrorMessage(null);
                    batch.setUpdatedAt(LocalDateTime.now());
                    batchRepository.save(batch);
                    throw new IllegalStateException("评估队列消费已被中断");
                }
                executeEvaluationQueueBatch(batch);
            }
        } finally {
            evaluationRunning.set(false);
            if (batchRepository.existsByEvaluationQueueStatus("QUEUED")) {
                submitEvaluationQueueWorker();
            }
        }
    }

    private ModelPredictionBatchDocument claimNextEvaluationBatch() {
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

    private void executeEvaluationQueueBatch(ModelPredictionBatchDocument batch) {
        try {
            batch.setEvaluationQueueStatus("RUNNING");
            batch.setEvaluationWaitReason(null);
            batch.setEvaluationStartedAt(LocalDateTime.now());
            batch.setEvaluationErrorMessage(null);
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            evaluateBatch(batch);
            ModelPredictionBatchDocument refreshed = batchRepository.findById(batch.getId()).orElseThrow();
            boolean pending = predictionRepository.existsByBatchIdAndEvaluationStatus(batch.getId(), "PENDING");
            refreshed.setEvaluationQueueStatus(pending ? "WAITING" : "COMPLETED");
            refreshed.setEvaluationWaitReason(pending ? EVALUATION_WAIT_PENDING_DETAILS : null);
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

    private void executePrediction(String batchId, List<String> stockCodes) {
        long startedNanos = System.nanoTime();
        ModelPredictionBatchDocument batch = batchRepository.findById(batchId).orElseThrow();
        try {
            batch.setStatus("RUNNING");
            batch.setStartedAt(LocalDateTime.now());
            batch.setProgressMessage("正在执行全市场预测");
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            int[] successCount = {0};
            int[] failedCount = {0};
            int[] processedCount = {0};
            Map<LocalDate, TradeDateResolution> targetDateCache = new HashMap<>();
            inferenceService.predictNextBatchChunks(
                    stockCodes, batch.getModelId(), batch.getBaseTradeDate(), PREDICTION_BATCH_SIZE,
                    (currentCodes, results) -> {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("预测队列消费已被中断");
                }
                Map<String, StockInfo> stockInfoMap = stockInfoRepository.findByCodeIn(currentCodes).stream()
                        .collect(Collectors.toMap(StockInfo::getCode, Function.identity(), (left, right) -> left));
                List<ModelPredictionDocument> documents = new ArrayList<>(results.size());
                LocalDateTime predictedAt = LocalDateTime.now();
                for (LstmPredictionResultDto result : results.values()) {
                    if (result.getBaseTradeDate() == null || result.getLastClosePrice() == null
                            || result.getPredictedChangeRatio() == null) {
                        continue;
                    }
                    if (!batch.getBaseTradeDate().equals(result.getBaseTradeDate())) {
                        log.warn("跳过基准交易日落后的个股预测: batchId={}, stockCode={}, expectedBaseDate={}, actualBaseDate={}",
                                batch.getId(), result.getStockCode(), batch.getBaseTradeDate(),
                                result.getBaseTradeDate());
                        continue;
                    }
                    StockInfo stockInfo = stockInfoMap.get(result.getStockCode());
                    ModelPredictionDocument document = new ModelPredictionDocument();
                    document.setBatchId(batch.getId());
                    document.setStockCode(result.getStockCode());
                    document.setStockName(stockInfo == null ? null : stockInfo.getName());
                    document.setIndustryCode(stockInfo == null ? null : stockInfo.getIndustryCode());
                    document.setModelId(batch.getModelId());
                    document.setModelVersion(batch.getModelVersion());
                    document.setBaseTradeDate(result.getBaseTradeDate());
                    TradeDateResolution resultTradeDate = targetDateCache.computeIfAbsent(
                            result.getBaseTradeDate(), this::resolveNextTradeDate);
                    document.setTargetTradeDate(resultTradeDate.tradeDate());
                    document.setCalendarFallback(resultTradeDate.calendarFallback());
                    if (resultTradeDate.calendarFallback()) {
                        batch.setCalendarFallback(true);
                    }
                    document.setBaseClosePrice(result.getLastClosePrice());
                    document.setPredictedClosePrice(result.getPredictedClosePrice());
                    document.setPredictedReturn(result.getPredictedChangeRatio());
                    document.setDirectionProbability(result.getDirectionProbability());
                    document.setDownsideRisk(result.getDownsideRisk());
                    document.setEvaluationStatus("PENDING");
                    document.setPredictedAt(predictedAt);
                    documents.add(document);
                }
                if (!documents.isEmpty()) {
                    predictionRepository.saveAll(documents);
                }
                processedCount[0] += currentCodes.size();
                successCount[0] += documents.size();
                failedCount[0] += currentCodes.size() - documents.size();
                batch.setProcessedStocks(processedCount[0]);
                batch.setSuccessCount(successCount[0]);
                batch.setFailedCount(failedCount[0]);
                batch.setCoverageRate(rate(successCount[0], stockCodes.size()));
                batch.setProgressMessage("已完成 " + processedCount[0] + "/" + stockCodes.size() + " 支股票预测");
                batch.setUpdatedAt(LocalDateTime.now());
                batchRepository.save(batch);
                log.info("全市场模型预测进度: batchId={}, processed={}/{}, success={}, failed={}, elapsedSeconds={}",
                        batch.getId(), processedCount[0], stockCodes.size(), successCount[0], failedCount[0],
                        elapsedSeconds(startedNanos));
                    });
            if (successCount[0] <= 0) {
                batch.setStatus("FAILED");
                batch.setErrorMessage("全市场预测未生成任何有效结果");
                batch.setProgressMessage("全市场预测失败：未生成有效结果");
                batch.setEvaluationQueueStatus("FAILED");
                batch.setEvaluationWaitReason(null);
                batch.setEvaluationErrorMessage("预测任务未生成有效结果，无法进入评估队列");
                batch.setFinishedAt(LocalDateTime.now());
                batch.setUpdatedAt(LocalDateTime.now());
                batchRepository.save(batch);
                log.error("全市场模型预测无有效结果: batchId={}, modelId={}, failed={}",
                        batch.getId(), batch.getModelId(), failedCount[0]);
                return;
            }
            batch.setStatus(failedCount[0] == 0 ? "COMPLETED" : "PARTIAL");
            batch.setFinishedAt(LocalDateTime.now());
            batch.setProgressMessage("全市场预测完成");
            batch.setEvaluationQueueStatus("WAITING");
            batch.setEvaluationWaitReason(EVALUATION_WAIT_MARKET_DATA);
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            refreshAbility(batch, predictionRepository.findByBatchId(batch.getId()));
            ModelPredictionBatchDocument completed = batchRepository.findById(batch.getId()).orElse(batch);
            if (enqueueEvaluationBatch(completed)) {
                submitEvaluationQueueWorker();
            }
            log.info("全市场模型预测完成: batchId={}, modelId={}, success={}, failed={}, elapsedSeconds={}",
                    batch.getId(), batch.getModelId(), successCount[0], failedCount[0], elapsedSeconds(startedNanos));
        } catch (CancellationException exception) {
            batch.setStatus("CANCELLED");
            batch.setProgressMessage("预测任务已停止");
            batch.setEvaluationQueueStatus("FAILED");
            batch.setEvaluationWaitReason(null);
            batch.setEvaluationErrorMessage("预测任务已停止，无法进入评估队列");
            batch.setFinishedAt(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            log.warn("全市场模型预测已停止: batchId={}, processed={}/{}",
                    batch.getId(), batch.getProcessedStocks(), stockCodes.size());
        } catch (Exception exception) {
            batch.setStatus("FAILED");
            batch.setErrorMessage(exception.getMessage());
            batch.setProgressMessage("全市场预测失败");
            batch.setEvaluationQueueStatus("FAILED");
            batch.setEvaluationWaitReason(null);
            batch.setEvaluationErrorMessage("预测任务失败，无法进入评估队列");
            batch.setFinishedAt(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            log.error("全市场模型预测失败: batchId={}", batchId, exception);
        }
    }

    private PredictionContext resolvePredictionContext() {
        String modelName = trainerService.getModelName();
        ModelActivationDocument activation = activationRepository.findByModelName(modelName)
                .orElseThrow(() -> new IllegalStateException("当前没有已激活模型"));
        LstmModelDocument model = modelRepository.findById(activation.getActiveModelVersionId())
                .orElseThrow(() -> new IllegalStateException("当前激活模型版本不存在"));
        trainerService.validateModelVersion(model.getId());
        return new PredictionContext(modelName, model);
    }

    private List<String> loadPredictionStockCodes() {
        List<String> stockCodes = stockInfoRepository.findAllCodes().stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("股票基础信息为空，无法执行全市场预测");
        }
        return stockCodes;
    }

    private PredictionBatchPreparation preparePredictionBatch(
            PredictionContext context, LocalDate baseTradeDate, LocalDate targetTradeDate,
            List<String> stockCodes, String triggeredBy) {
        ModelPredictionBatchDocument existing = batchRepository
                .findFirstByModelIdAndBaseTradeDateOrderByCreatedAtDesc(
                        context.model().getId(), baseTradeDate);
        if (hasStatus(existing, RUNNING_STATUSES)
                || isReusablePredictionBatch(existing)) {
            return new PredictionBatchPreparation(existing, false);
        }
        if (existing != null) {
            predictionRepository.deleteByBatchId(existing.getId());
            log.info("准备补充预测时清理不可复用批次明细: batchId={}, status={}, successCount={}",
                    existing.getId(), existing.getStatus(), existing.getSuccessCount());
        }
        LocalDate resolvedTargetTradeDate = resolveNextTradeDate(baseTradeDate).tradeDate();
        if (!resolvedTargetTradeDate.equals(targetTradeDate)) {
            throw new IllegalStateException("预测基准日与目标交易日不连续: baseTradeDate=" + baseTradeDate
                    + ", expectedTargetTradeDate=" + targetTradeDate
                    + ", actualTargetTradeDate=" + resolvedTargetTradeDate);
        }
        LocalDateTime now = LocalDateTime.now();
        ModelPredictionBatchDocument batch = new ModelPredictionBatchDocument();
        batch.setModelName(context.modelName());
        batch.setModelId(context.model().getId());
        batch.setModelVersion(context.model().getModelVersion());
        batch.setBaseTradeDate(baseTradeDate);
        batch.setTargetTradeDate(targetTradeDate);
        batch.setInformationCutoffAt(now);
        batch.setCalendarFallback(false);
        batch.setStatus("QUEUED");
        batch.setTotalStocks(stockCodes.size());
        batch.setAbilityLevel("INSUFFICIENT_DATA");
        batch.setAbilityMessage("预测尚未完成");
        batch.setProgressMessage("预测任务已排队");
        batch.setEvaluationQueueStatus("WAITING");
        batch.setEvaluationWaitReason(EVALUATION_WAIT_PREDICTION);
        batch.setTriggeredBy(triggeredBy);
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        return new PredictionBatchPreparation(batchRepository.save(batch), true);
    }

    private boolean isReusablePredictionBatch(ModelPredictionBatchDocument batch) {
        if (batch == null || batch.getId() == null
                || !hasStatus(batch, EVALUATABLE_STATUSES)
                || batch.getSuccessCount() <= 0) {
            return false;
        }
        long detailCount = predictionRepository.countByBatchId(batch.getId());
        if (detailCount != batch.getSuccessCount()) {
            log.warn("预测批次统计与明细数量不一致，按缺失数据处理: batchId={}, successCount={}, detailCount={}",
                    batch.getId(), batch.getSuccessCount(), detailCount);
            return false;
        }
        return true;
    }

    private void refreshAbility(ModelPredictionBatchDocument batch, List<ModelPredictionDocument> allPredictions) {
        updateCrossSectionalPercentiles(allPredictions);
        List<ModelPredictionDocument> evaluated = allPredictions.stream()
                .filter(item -> "EVALUATED".equals(item.getEvaluationStatus()))
                .filter(item -> item.getPredictedReturn() != null && Double.isFinite(item.getPredictedReturn()))
                .filter(item -> item.getActualReturn() != null && Double.isFinite(item.getActualReturn()))
                .toList();
        int noMarketDataCount = (int) allPredictions.stream()
                .filter(item -> "NO_MARKET_DATA".equals(item.getEvaluationStatus())).count();
        batch.setEvaluatedCount(evaluated.size());
        batch.setNoMarketDataCount(noMarketDataCount);
        batch.setEvaluationRate(rate(evaluated.size(), Math.max(1, batch.getSuccessCount())));
        if (evaluated.isEmpty()) {
            batch.setDirectionAuc(null);
            batch.setBrierScore(null);
            batch.setBrierBaseline(null);
            batch.setExpectedCalibrationError(null);
            batch.setCalibrationSampleCount(0);
            batch.setAbilityLevel("INSUFFICIENT_DATA");
            batch.setIterationRecommended(false);
            batch.setAbilityMessage("目标交易日行情尚未完整形成，需继续等待实际结果");
            batch.setTradingEligibility("DISABLED");
            batch.setTradingEligibilityReason("尚无可评估的样本，禁止进入买入流程");
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            return;
        }

        double directionAccuracy = evaluated.stream()
                .filter(item -> Boolean.TRUE.equals(item.getDirectionCorrect())).count() / (double) evaluated.size();
        double mae = evaluated.stream().mapToDouble(ModelPredictionDocument::getAbsoluteError).average().orElse(0D);
        double rmse = Math.sqrt(evaluated.stream().mapToDouble(ModelPredictionDocument::getSquaredError)
                .average().orElse(0D));
        double[] predicted = evaluated.stream().mapToDouble(ModelPredictionDocument::getPredictedReturn).toArray();
        double[] actual = evaluated.stream().mapToDouble(ModelPredictionDocument::getActualReturn).toArray();
        List<ModelPredictionDocument> withProbability = evaluated.stream()
                .filter(item -> item.getDirectionProbability() != null
                        && Double.isFinite(item.getDirectionProbability())
                        && item.getDirectionProbability() >= 0D
                        && item.getDirectionProbability() <= 1D)
                .toList();
        double actualUpRate = evaluated.stream().filter(item -> item.getActualReturn() > 0D).count()
                / (double) evaluated.size();
        double constantBaselineAccuracy = Math.max(actualUpRate, 1D - actualUpRate);
        List<ModelPredictionDocument> calibrationSamples = withProbability;
        Double directionAuc = areaUnderRoc(calibrationSamples);
        Double brierScore = brierScore(calibrationSamples);
        double calibrationUpRate = calibrationSamples.stream().filter(item -> item.getActualReturn() > 0D).count()
                / (double) Math.max(1, calibrationSamples.size());
        Double brierBaseline = calibrationSamples.isEmpty() ? null
                : calibrationUpRate * (1D - calibrationUpRate);
        Double expectedCalibrationError = expectedCalibrationError(calibrationSamples);
        double directionProbabilityStd = standardDeviation(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).toArray());
        double predictedReturnStd = standardDeviation(predicted);
        double pearson = correlation(predicted, actual);
        double spearman = correlation(rank(predicted), rank(actual));
        List<ModelPredictionDocument> sorted = evaluated.stream()
                .sorted(Comparator.comparing(ModelPredictionDocument::getPredictedReturn).reversed())
                .toList();
        int topCount = Math.max(1, (int) Math.ceil(sorted.size() * 0.1D));
        List<ModelPredictionDocument> top = sorted.subList(0, topCount);
        double topAverageReturn = top.stream().mapToDouble(ModelPredictionDocument::getActualReturn).average().orElse(0D);
        double topDirectionAccuracy = top.stream().filter(item -> item.getActualReturn() > 0D).count()
                / (double) top.size();
        List<ModelPredictionDocument> highConfidence = evaluated.stream()
                .filter(item -> item.getDirectionProbability() != null
                        && (item.getDirectionProbability() >= 0.65D || item.getDirectionProbability() <= 0.35D))
                .toList();
        Double highConfidenceAccuracy = highConfidence.isEmpty() ? null
                : highConfidence.stream().filter(item -> Boolean.TRUE.equals(item.getDirectionCorrect())).count()
                / (double) highConfidence.size();
        List<ModelPredictionDocument> highRisk = evaluated.stream()
                .filter(item -> item.getDownsideRisk() != null && item.getDownsideRisk() >= 0.60D)
                .toList();
        Double downsideHitRate = highRisk.isEmpty() ? null
                : highRisk.stream().filter(item -> item.getActualReturn() < 0D).count() / (double) highRisk.size();

        String abilityLevel;
        String abilityMessage;
        if (evaluated.size() < MINIMUM_ABILITY_SAMPLE_COUNT) {
            abilityLevel = "INSUFFICIENT_DATA";
            abilityMessage = "已评估样本不足 100 条，当前结果只用于链路验证";
        } else if (directionAccuracy >= 0.55D && spearman >= 0.05D && mae <= 0.03D
                && topAverageReturn > 0D) {
            abilityLevel = "STRONG";
            abilityMessage = "方向、排序、误差和高分股票表现达到当前工程基线";
        } else if (directionAccuracy >= 0.50D && spearman >= 0D && mae <= 0.05D) {
            abilityLevel = "ACCEPTABLE";
            abilityMessage = "模型达到最低可用基线，仍需积累多交易日样本";
        } else {
            abilityLevel = "WEAK";
            abilityMessage = "核心预测指标低于基线，建议检查数据周期、特征和训练参数";
        }

        boolean collapsed = directionProbabilityStd < MIN_DIRECTION_PROBABILITY_STD
                || predictedReturnStd < MIN_PREDICTED_RETURN_STD;
        boolean beatsBaseline = directionAccuracy >= constantBaselineAccuracy + MIN_BASELINE_ACCURACY_LIFT;
        String tradingEligibility = "OBSERVE_ONLY";
        String tradingEligibilityReason;
        if (evaluated.size() < MINIMUM_ABILITY_SAMPLE_COUNT
                || calibrationSamples.size() < MINIMUM_CALIBRATION_SAMPLE_COUNT) {
            tradingEligibility = "DISABLED";
            tradingEligibilityReason = "有效回填或概率校准样本不足，不能证明模型具备交易能力";
        } else if (collapsed) {
            tradingEligibilityReason = "模型输出区分度不足：上涨概率或预测收益标准差过低";
        } else if (!beatsBaseline || spearman <= 0D || topAverageReturn <= 0D) {
            tradingEligibilityReason = "模型未稳定超过多数方向基线，或 Top 10% 排序收益未为正";
        } else {
            tradingEligibilityReason = "单个交易日不能授予真实交易资格，需通过滚动窗口和模拟交易门禁";
        }

        batch.setDirectionAccuracy(directionAccuracy);
        batch.setActualUpRate(actualUpRate);
        batch.setConstantBaselineAccuracy(constantBaselineAccuracy);
        batch.setDirectionAuc(directionAuc);
        batch.setBrierScore(brierScore);
        batch.setBrierBaseline(brierBaseline);
        batch.setExpectedCalibrationError(expectedCalibrationError);
        batch.setCalibrationSampleCount(calibrationSamples.size());
        batch.setDirectionProbabilityMean(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).average().orElse(0D));
        batch.setDirectionProbabilityStd(directionProbabilityStd);
        batch.setDirectionProbabilityMin(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).min().orElse(0D));
        batch.setDirectionProbabilityMax(withProbability.stream()
                .mapToDouble(ModelPredictionDocument::getDirectionProbability).max().orElse(0D));
        batch.setPredictedReturnStd(predictedReturnStd);
        batch.setReturnMae(mae);
        batch.setReturnRmse(rmse);
        batch.setPearsonIc(pearson);
        batch.setSpearmanRankIc(spearman);
        batch.setTopDecileAverageReturn(topAverageReturn);
        batch.setTopDecileDirectionAccuracy(topDirectionAccuracy);
        batch.setHighConfidenceAccuracy(highConfidenceAccuracy);
        batch.setDownsideRiskHitRate(downsideHitRate);
        batch.setAbilityLevel(abilityLevel);
        batch.setIterationRecommended("WEAK".equals(abilityLevel));
        batch.setAbilityMessage(abilityMessage + "；交易门禁：" + tradingEligibilityReason);
        batch.setTradingEligibility(tradingEligibility);
        batch.setTradingEligibilityReason(tradingEligibilityReason);
        refreshRollingAssessment(batch);
        long pendingCount = allPredictions.stream()
                .filter(item -> "PENDING".equals(item.getEvaluationStatus())).count();
        if (pendingCount == 0 && !hasStatus(batch, RUNNING_STATUSES)) {
            batch.setStatus("EVALUATED");
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
    }

    private void resetPredictionEvaluationDetails(String batchId) {
        Query query = Query.query(Criteria.where("batchId").is(batchId));
        Update update = new Update()
                .set("evaluationStatus", "PENDING")
                .unset("actualClosePrice")
                .unset("actualReturn")
                .unset("returnError")
                .unset("absoluteError")
                .unset("squaredError")
                .unset("directionCorrect")
                .unset("evaluatedAt");
        mongoTemplate.updateMulti(query, update, ModelPredictionDocument.class);
    }

    private void resetEvaluationSummary(ModelPredictionBatchDocument batch, String abilityMessage) {
        batch.setEvaluatedCount(0);
        batch.setNoMarketDataCount(0);
        batch.setEvaluationRate(null);
        batch.setDirectionAccuracy(null);
        batch.setActualUpRate(null);
        batch.setConstantBaselineAccuracy(null);
        batch.setDirectionAuc(null);
        batch.setBrierScore(null);
        batch.setBrierBaseline(null);
        batch.setExpectedCalibrationError(null);
        batch.setCalibrationSampleCount(0);
        batch.setDirectionProbabilityMean(null);
        batch.setDirectionProbabilityStd(null);
        batch.setDirectionProbabilityMin(null);
        batch.setDirectionProbabilityMax(null);
        batch.setPredictedReturnStd(null);
        batch.setRollingWindowBatchCount(null);
        batch.setRollingWindowSampleCount(null);
        batch.setRollingAverageSpearmanRankIc(null);
        batch.setRollingAverageTopDecileReturn(null);
        batch.setReturnMae(null);
        batch.setReturnRmse(null);
        batch.setPearsonIc(null);
        batch.setSpearmanRankIc(null);
        batch.setTopDecileAverageReturn(null);
        batch.setTopDecileDirectionAccuracy(null);
        batch.setHighConfidenceAccuracy(null);
        batch.setDownsideRiskHitRate(null);
        batch.setAbilityLevel("INSUFFICIENT_DATA");
        batch.setIterationRecommended(false);
        batch.setAbilityMessage(abilityMessage);
        batch.setTradingEligibility("DISABLED");
        batch.setTradingEligibilityReason("评估尚未完成，禁止进入买入流程");
    }

    private ModelPredictionBatchDocument resolveBatch(String batchId) {
        if (batchId != null && !batchId.isBlank()) {
            return batchRepository.findById(batchId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("预测批次不存在"));
        }
        ModelPredictionBatchDocument latest = batchRepository.findTopByOrderByCreatedAtDesc();
        if (latest == null) {
            throw new IllegalStateException("暂无预测批次");
        }
        return latest;
    }

    private TradeDateResolution resolveNextTradeDate(LocalDate baseTradeDate) {
        return new TradeDateResolution(tradingCalendarService.nextTradingDay(baseTradeDate), false);
    }

    private ModelPredictionAnalysisDto.BatchItem toBatchItem(ModelPredictionBatchDocument item) {
        return ModelPredictionAnalysisDto.BatchItem.builder()
                .id(item.getId()).modelName(item.getModelName()).modelId(item.getModelId())
                .modelVersion(item.getModelVersion()).baseTradeDate(item.getBaseTradeDate())
                .targetTradeDate(item.getTargetTradeDate()).informationCutoffAt(item.getInformationCutoffAt())
                .calendarFallback(item.getCalendarFallback()).status(item.getStatus())
                .totalStocks(item.getTotalStocks()).processedStocks(item.getProcessedStocks())
                .successCount(item.getSuccessCount()).failedCount(item.getFailedCount())
                .evaluatedCount(item.getEvaluatedCount()).noMarketDataCount(item.getNoMarketDataCount())
                .evaluationQueueStatus(item.getEvaluationQueueStatus())
                .evaluationWaitReason(item.getEvaluationWaitReason())
                .evaluationQueuedAt(item.getEvaluationQueuedAt())
                .evaluationStartedAt(item.getEvaluationStartedAt())
                .evaluationFinishedAt(item.getEvaluationFinishedAt())
                .evaluationErrorMessage(item.getEvaluationErrorMessage())
                .coverageRate(item.getCoverageRate()).evaluationRate(item.getEvaluationRate())
                .abilityLevel(item.getAbilityLevel()).iterationRecommended(item.isIterationRecommended())
                .abilityMessage(item.getAbilityMessage()).tradingEligibility(item.getTradingEligibility())
                .tradingEligibilityReason(item.getTradingEligibilityReason())
                .progressMessage(item.getProgressMessage())
                .triggeredBy(item.getTriggeredBy()).startedAt(item.getStartedAt())
                .finishedAt(item.getFinishedAt()).errorMessage(item.getErrorMessage())
                .createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt()).build();
    }

    private ModelPredictionAnalysisDto.PredictionItem toPredictionItem(ModelPredictionDocument item) {
        return ModelPredictionAnalysisDto.PredictionItem.builder()
                .id(item.getId()).batchId(item.getBatchId()).stockCode(item.getStockCode())
                .stockName(item.getStockName()).industryCode(item.getIndustryCode())
                .modelId(item.getModelId()).modelVersion(item.getModelVersion())
                .baseTradeDate(item.getBaseTradeDate()).targetTradeDate(item.getTargetTradeDate())
                .calendarFallback(item.getCalendarFallback())
                .baseClosePrice(item.getBaseClosePrice()).predictedClosePrice(item.getPredictedClosePrice())
                .predictedReturn(item.getPredictedReturn()).directionProbability(item.getDirectionProbability())
                .downsideRisk(item.getDownsideRisk())
                .predictedReturnPercentile(item.getPredictedReturnPercentile())
                .directionProbabilityPercentile(item.getDirectionProbabilityPercentile())
                .actualClosePrice(item.getActualClosePrice())
                .actualReturn(item.getActualReturn()).returnError(item.getReturnError())
                .absoluteError(item.getAbsoluteError()).directionCorrect(item.getDirectionCorrect())
                .evaluationStatus(item.getEvaluationStatus()).predictedAt(item.getPredictedAt())
                .evaluatedAt(item.getEvaluatedAt()).build();
    }

    private ModelPredictionAnalysisDto.AbilityResponse toAbility(ModelPredictionBatchDocument item) {
        return ModelPredictionAnalysisDto.AbilityResponse.builder()
                .batchId(item.getId()).modelId(item.getModelId()).modelVersion(item.getModelVersion())
                .baseTradeDate(item.getBaseTradeDate()).targetTradeDate(item.getTargetTradeDate())
                .predictedCount(item.getSuccessCount()).evaluatedCount(item.getEvaluatedCount())
                .coverageRate(item.getCoverageRate()).evaluationRate(item.getEvaluationRate())
                .directionAccuracy(item.getDirectionAccuracy()).actualUpRate(item.getActualUpRate())
                .constantBaselineAccuracy(item.getConstantBaselineAccuracy())
                .directionAuc(item.getDirectionAuc()).brierScore(item.getBrierScore())
                .brierBaseline(item.getBrierBaseline())
                .expectedCalibrationError(item.getExpectedCalibrationError())
                .calibrationSampleCount(item.getCalibrationSampleCount())
                .directionProbabilityStd(item.getDirectionProbabilityStd())
                .predictedReturnStd(item.getPredictedReturnStd())
                .rollingWindowBatchCount(item.getRollingWindowBatchCount())
                .rollingWindowSampleCount(item.getRollingWindowSampleCount())
                .rollingAverageSpearmanRankIc(item.getRollingAverageSpearmanRankIc())
                .rollingAverageTopDecileReturn(item.getRollingAverageTopDecileReturn())
                .returnMae(item.getReturnMae())
                .returnRmse(item.getReturnRmse()).pearsonIc(item.getPearsonIc())
                .spearmanRankIc(item.getSpearmanRankIc())
                .topDecileAverageReturn(item.getTopDecileAverageReturn())
                .topDecileDirectionAccuracy(item.getTopDecileDirectionAccuracy())
                .highConfidenceAccuracy(item.getHighConfidenceAccuracy())
                .downsideRiskHitRate(item.getDownsideRiskHitRate())
                .abilityLevel(item.getAbilityLevel()).iterationRecommended(item.isIterationRecommended())
                .abilityMessage(item.getAbilityMessage()).tradingEligibility(item.getTradingEligibility())
                .tradingEligibilityReason(item.getTradingEligibilityReason()).build();
    }

    private void refreshRollingAssessment(ModelPredictionBatchDocument batch) {
        if (isHistoricalReconstructionBatch(batch)) {
            batch.setRollingWindowBatchCount(0);
            batch.setRollingWindowSampleCount(0);
            batch.setRollingAverageSpearmanRankIc(null);
            batch.setRollingAverageTopDecileReturn(null);
            batch.setTradingEligibility("DISABLED");
            batch.setTradingEligibilityReason("历史补缺批次使用当前模型重建，仅用于数据分析，不参与真实交易门禁");
            return;
        }
        List<ModelPredictionBatchDocument> history = batchRepository
                .findByModelIdAndStatusInOrderByTargetTradeDateDesc(
                        batch.getModelId(), List.of("COMPLETED", "PARTIAL", "EVALUATED"))
                .stream()
                .filter(item -> !isHistoricalReconstructionBatch(item))
                .filter(item -> item.getEvaluatedCount() >= MINIMUM_ABILITY_SAMPLE_COUNT)
                .limit(ROLLING_WINDOW_BATCHES)
                .collect(Collectors.toCollection(ArrayList::new));
        if (history.stream().noneMatch(item -> batch.getId().equals(item.getId()))) {
            history.add(0, batch);
        }
        history = history.stream().limit(ROLLING_WINDOW_BATCHES).toList();
        int sampleCount = history.stream().mapToInt(ModelPredictionBatchDocument::getEvaluatedCount).sum();
        double averageSpearman = history.stream().map(ModelPredictionBatchDocument::getSpearmanRankIc)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D);
        double averageTopReturn = history.stream().map(ModelPredictionBatchDocument::getTopDecileAverageReturn)
                .filter(Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(0D);
        batch.setRollingWindowBatchCount(history.size());
        batch.setRollingWindowSampleCount(sampleCount);
        batch.setRollingAverageSpearmanRankIc(averageSpearman);
        batch.setRollingAverageTopDecileReturn(averageTopReturn);
        if (!"DISABLED".equals(batch.getTradingEligibility())) {
            batch.setTradingEligibility("OBSERVE_ONLY");
            batch.setTradingEligibilityReason("滚动窗口 " + history.size() + "/" + ROLLING_WINDOW_BATCHES
                    + " 个批次、" + sampleCount + "/" + MINIMUM_ROLLING_SAMPLE_COUNT
                    + " 个样本；且需完成模拟交易验证后才能开放买入");
        }
    }

    private void updateCrossSectionalPercentiles(List<ModelPredictionDocument> predictions) {
        List<ModelPredictionDocument> returnRankable = predictions.stream()
                .filter(item -> item.getPredictedReturn() != null && Double.isFinite(item.getPredictedReturn()))
                .toList();
        List<ModelPredictionDocument> probabilityRankable = predictions.stream()
                .filter(item -> item.getDirectionProbability() != null
                        && Double.isFinite(item.getDirectionProbability()))
                .toList();
        assignPercentiles(returnRankable, true);
        assignPercentiles(probabilityRankable, false);
        if (!returnRankable.isEmpty() || !probabilityRankable.isEmpty()) {
            predictionRepository.saveAll(predictions);
        }
    }

    private boolean isHistoricalReconstructionBatch(ModelPredictionBatchDocument batch) {
        if (batch == null) {
            return false;
        }
        if (batch.getRerunSourceBatchId() != null && !batch.getRerunSourceBatchId().isBlank()) {
            return true;
        }
        if (batch.getTriggeredBy() == null) {
            return false;
        }
        return HISTORICAL_BACKFILL_TRIGGER.equals(batch.getTriggeredBy())
                || batch.getTriggeredBy().startsWith(MANUAL_BACKFILL_TRIGGER_PREFIX);
    }

    private boolean hasStatus(ModelPredictionBatchDocument batch, List<String> statuses) {
        return batch != null && batch.getStatus() != null && statuses.contains(batch.getStatus());
    }

    private void assignPercentiles(List<ModelPredictionDocument> items, boolean returnMetric) {
        if (items.isEmpty()) {
            return;
        }
        List<ModelPredictionDocument> sorted = items.stream()
                .sorted(Comparator.comparingDouble(item -> returnMetric
                        ? item.getPredictedReturn() : item.getDirectionProbability()))
                .toList();
        int denominator = Math.max(1, sorted.size() - 1);
        for (int index = 0; index < sorted.size(); index++) {
            double percentile = index / (double) denominator;
            if (returnMetric) {
                sorted.get(index).setPredictedReturnPercentile(percentile);
            } else {
                sorted.get(index).setDirectionProbabilityPercentile(percentile);
            }
        }
    }

    private double standardDeviation(double[] values) {
        if (values.length < 2) {
            return 0D;
        }
        double mean = java.util.Arrays.stream(values).average().orElse(0D);
        return Math.sqrt(java.util.Arrays.stream(values)
                .map(value -> Math.pow(value - mean, 2D)).average().orElse(0D));
    }

    /**
     * 计算上涨概率的 ROC AUC。
     *
     * 按概率排序并对相同概率使用平均秩，概率更高的正样本获得更高秩；
     * 实际标签只有单一类别时返回空值，避免把不可计算的 AUC 误记为 0.5。
     *
     * @param samples 同时包含预测概率和实际收益的有效样本
     * @return ROC AUC，或无法计算时的空值
     */
    private Double areaUnderRoc(List<ModelPredictionDocument> samples) {
        long positiveCount = samples.stream().filter(item -> item.getActualReturn() > 0D).count();
        long negativeCount = samples.size() - positiveCount;
        if (positiveCount == 0L || negativeCount == 0L) {
            return null;
        }
        List<ModelPredictionDocument> sorted = samples.stream()
                .sorted(Comparator.comparingDouble(ModelPredictionDocument::getDirectionProbability))
                .toList();
        double positiveRankSum = 0D;
        int position = 0;
        while (position < sorted.size()) {
            int end = position + 1;
            while (end < sorted.size() && Double.compare(
                    sorted.get(position).getDirectionProbability(),
                    sorted.get(end).getDirectionProbability()) == 0) {
                end++;
            }
            double averageRank = (position + end - 1) / 2D + 1D;
            for (int index = position; index < end; index++) {
                if (sorted.get(index).getActualReturn() > 0D) {
                    positiveRankSum += averageRank;
                }
            }
            position = end;
        }
        double positiveBaselineRankSum = positiveCount * (positiveCount + 1D) / 2D;
        return (positiveRankSum - positiveBaselineRankSum)
                / (positiveCount * (double) negativeCount);
    }

    /**
     * 计算二分类概率的 Brier Score。
     *
     * @param samples 同时包含预测概率和实际收益的有效样本
     * @return Brier Score，样本为空时返回空值
     */
    private Double brierScore(List<ModelPredictionDocument> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        return samples.stream()
                .mapToDouble(item -> {
                    double label = item.getActualReturn() > 0D ? 1D : 0D;
                    double error = item.getDirectionProbability() - label;
                    return error * error;
                })
                .average()
                .orElse(0D);
    }

    /**
     * 使用十个等宽概率桶计算期望校准误差。
     *
     * @param samples 同时包含预测概率和实际收益的有效样本
     * @return ECE，样本为空时返回空值
     */
    private Double expectedCalibrationError(List<ModelPredictionDocument> samples) {
        if (samples.isEmpty()) {
            return null;
        }
        double weightedError = 0D;
        for (int bucket = 0; bucket < 10; bucket++) {
            final int currentBucket = bucket;
            final double lowerBound = currentBucket / 10D;
            final double upperBound = (currentBucket + 1) / 10D;
            List<ModelPredictionDocument> bucketSamples = samples.stream()
                    .filter(item -> {
                        double probability = item.getDirectionProbability();
                        return currentBucket == 9
                                ? probability >= lowerBound && probability <= upperBound
                                : probability >= lowerBound && probability < upperBound;
                    })
                    .toList();
            if (bucketSamples.isEmpty()) {
                continue;
            }
            double predictedMean = bucketSamples.stream()
                    .mapToDouble(ModelPredictionDocument::getDirectionProbability)
                    .average().orElse(0D);
            double actualMean = bucketSamples.stream()
                    .filter(item -> item.getActualReturn() > 0D)
                    .count() / (double) bucketSamples.size();
            weightedError += Math.abs(predictedMean - actualMean)
                    * bucketSamples.size() / samples.size();
        }
        return weightedError;
    }

    private double[] rank(double[] values) {
        Integer[] indexes = new Integer[values.length];
        for (int index = 0; index < values.length; index++) {
            indexes[index] = index;
        }
        java.util.Arrays.sort(indexes, Comparator.comparingDouble(index -> values[index]));
        double[] ranks = new double[values.length];
        int position = 0;
        while (position < indexes.length) {
            int end = position + 1;
            while (end < indexes.length && Double.compare(
                    values[indexes[position]], values[indexes[end]]) == 0) {
                end++;
            }
            double averageRank = (position + end - 1) / 2D + 1D;
            for (int index = position; index < end; index++) {
                ranks[indexes[index]] = averageRank;
            }
            position = end;
        }
        return ranks;
    }

    private double correlation(double[] left, double[] right) {
        if (left.length != right.length || left.length < 2) {
            return 0D;
        }
        double leftAverage = java.util.Arrays.stream(left).average().orElse(0D);
        double rightAverage = java.util.Arrays.stream(right).average().orElse(0D);
        double covariance = 0D;
        double leftVariance = 0D;
        double rightVariance = 0D;
        for (int index = 0; index < left.length; index++) {
            double leftDelta = left[index] - leftAverage;
            double rightDelta = right[index] - rightAverage;
            covariance += leftDelta * rightDelta;
            leftVariance += leftDelta * leftDelta;
            rightVariance += rightDelta * rightDelta;
        }
        double denominator = Math.sqrt(leftVariance * rightVariance);
        return denominator == 0D ? 0D : covariance / denominator;
    }

    private Double rate(int numerator, int denominator) {
        return denominator <= 0 ? 0D : numerator / (double) denominator;
    }

    private boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private long elapsedSeconds(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000_000L);
    }

    private String normalizeOperator(String operatorName) {
        return operatorName == null || operatorName.isBlank() ? "web-console" : operatorName.trim();
    }

    private record TradeDateResolution(LocalDate tradeDate, boolean calendarFallback) {
    }

    private record PredictionContext(String modelName, LstmModelDocument model) {
    }

    private record PredictionBatchPreparation(ModelPredictionBatchDocument batch, boolean created) {
    }
}
// AI_GENERATE_END------------ -------------
