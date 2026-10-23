// AI_GENERATE_START----------------------
package com.stock.model.service;

import com.stock.data.repository.PriceRepository;
import com.stock.data.repository.StockInfoRepository;
import com.stock.data.service.TradingCalendarService;
import com.stock.model.config.PredictionBackfillProperties;
import com.stock.model.dto.PredictionBackfillResponse;
import com.stock.model.dto.PredictionOperationResponse;
import com.stock.model.dto.PredictionRerunRequest;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelPredictionRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 全市场模型预测批次规划与统一调度服务。
 *
 * 负责历史缺口扫描、预测批次创建、历史重算和预测评估队列的统一编排。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Slf4j
@Service
public class ModelPredictionAnalysisService {

    private static final List<String> RUNNING_STATUSES = List.of("QUEUED", "RUNNING");
    private static final List<String> EVALUATABLE_STATUSES = List.of("COMPLETED", "PARTIAL", "EVALUATED");
    private static final List<String> PRESENT_PREDICTION_STATUSES =
            List.of("QUEUED", "RUNNING", "COMPLETED", "PARTIAL", "EVALUATED");
    private static final String BATCH_STOCK_UNIQUE_INDEX = "uk_prediction_batch_stock";
    private static final String HISTORICAL_BACKFILL_TRIGGER = "scheduler-backfill";
    private static final String EVALUATION_WAIT_PREDICTION = "PREDICTION_NOT_COMPLETED";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

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
    private final ModelPredictionQueryService predictionQueryService;
    private final PredictionExecutionQueueService predictionQueueService;
    private final PredictionEvaluationQueueService evaluationQueueService;
    private final AtomicBoolean backfillScanRunning = new AtomicBoolean(false);

    /**
     * 创建模型预测批次规划服务。
     *
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
     * @param predictionQueryService 预测只读查询服务
     * @param predictionQueueService 预测持久化队列服务
     * @param evaluationQueueService 评估持久化队列服务
     */
    public ModelPredictionAnalysisService(
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
            ModelPredictionQueryService predictionQueryService,
            PredictionExecutionQueueService predictionQueueService,
            PredictionEvaluationQueueService evaluationQueueService) {
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
        this.predictionQueryService = predictionQueryService;
        this.predictionQueueService = predictionQueueService;
        this.evaluationQueueService = evaluationQueueService;
    }

    /**
     * 应用重启后恢复遗留队列状态并补齐历史批次元数据。
     */
    @PostConstruct
    public void recoverInterruptedPredictionBatches() {
        ensurePredictionIndex();
        predictionQueueService.recoverInterruptedBatches();
        evaluationQueueService.recoverInterruptedBatches();
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

    /**
     * 使用原批次绑定的模型版本和历史基准日重新生成预测。
     *
     * @param request 历史预测重算请求
     * @return 新建或复用的历史预测批次信息
     */
    public PredictionOperationResponse rerunPrediction(PredictionRerunRequest request) {
        String sourceBatchId = request == null ? null : request.getBatchId();
        ModelPredictionBatchDocument source = predictionQueryService.resolveBatch(sourceBatchId);
        ModelPredictionBatchDocument existingRerun = batchRepository
                .findFirstByRerunSourceBatchIdOrderByCreatedAtDesc(source.getId());
        if (existingRerun != null) {
            return PredictionOperationResponse.builder()
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
                .map(ModelPredictionDocument::getStockCode)
                .filter(code -> code != null && !code.isBlank())
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
            return PredictionOperationResponse.builder()
                    .batchId(concurrent.getId()).status(concurrent.getStatus())
                    .message("该历史批次已存在重算结果，已返回原重算批次").build();
        }
        predictionQueueService.submitWorker();
        return PredictionOperationResponse.builder()
                .batchId(saved.getId()).status(saved.getStatus()).message("历史预测重算任务已提交").build();
    }

    /**
     * 扫描并流水线消费每日预测和评估队列。
     *
     * @return 扫描、预测入队和评估入队汇总结果
     */
    public PredictionBackfillResponse startScheduledPrediction() {
        PredictionBackfillResponse response;
        RuntimeException predictionFailure = null;
        int evaluationQueuedCount = 0;
        try {
            response = scanAndQueuePredictionBackfill("scheduler");
            evaluationQueuedCount += evaluationQueueService.enqueuePending();
            evaluationQueueService.submitWorker();
            predictionQueueService.awaitWorker(predictionQueueService.submitWorker());
        } catch (RuntimeException exception) {
            predictionFailure = exception;
            response = buildBackfillResponse(0, 0, 0, 0, 0, null);
            log.error("统一任务预测阶段失败，继续执行历史评估阶段: reason={}", exception.getMessage(), exception);
        }
        try {
            evaluationQueuedCount += evaluationQueueService.enqueuePending();
            evaluationQueueService.awaitWorker(evaluationQueueService.submitWorker());
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

    private void ensurePredictionIndex() {
        mongoTemplate.indexOps(ModelPredictionDocument.class).ensureIndex(new Index()
                .on("batchId", Sort.Direction.ASC)
                .on("stockCode", Sort.Direction.ASC)
                .unique().named(BATCH_STOCK_UNIQUE_INDEX));
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
            if (hasStatus(existing, RUNNING_STATUSES) || isReusablePredictionBatch(existing)) {
                existingPredictionCount++;
            } else {
                LocalDate baseTradeDate = tradingCalendarService.previousTradingDay(targetTradeDate);
                if (!hasRequiredPriceCoverage(stockCodes, baseTradeDate)) {
                    waitingForMarketDataCount++;
                    log.warn("预测缺口暂不入队，基准日行情覆盖率不足: baseTradeDate={}, targetTradeDate={}",
                            baseTradeDate, targetTradeDate);
                } else {
                    String batchTriggeredBy = "scheduler".equals(triggeredBy) && !targetTradeDate.equals(today)
                            ? HISTORICAL_BACKFILL_TRIGGER : triggeredBy;
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
                .message(message).build();
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
        return rate((int) Math.min(priceCount, Integer.MAX_VALUE), stockCodes.size())
                >= predictionBackfillProperties.resolveMinPriceCoverageRate();
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
                .map(String::trim).distinct().sorted().toList();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("股票基础信息为空，无法执行全市场预测");
        }
        return stockCodes;
    }

    private PredictionBatchPreparation preparePredictionBatch(
            PredictionContext context, LocalDate baseTradeDate, LocalDate targetTradeDate,
            List<String> stockCodes, String triggeredBy) {
        ModelPredictionBatchDocument existing = batchRepository
                .findFirstByModelIdAndBaseTradeDateOrderByCreatedAtDesc(context.model().getId(), baseTradeDate);
        if (hasStatus(existing, RUNNING_STATUSES) || isReusablePredictionBatch(existing)) {
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

    private TradeDateResolution resolveNextTradeDate(LocalDate baseTradeDate) {
        return new TradeDateResolution(tradingCalendarService.nextTradingDay(baseTradeDate), false);
    }

    private boolean hasStatus(ModelPredictionBatchDocument batch, List<String> statuses) {
        return batch != null && batch.getStatus() != null && statuses.contains(batch.getStatus());
    }

    private Double rate(int numerator, int denominator) {
        return denominator <= 0 ? 0D : numerator / (double) denominator;
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
// AI_GENERATE_END----------------------