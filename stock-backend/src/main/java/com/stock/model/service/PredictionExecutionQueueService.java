// AI_GENERATE_START---
package com.stock.model.service;

import com.stock.data.entity.StockInfo;
import com.stock.data.repository.StockInfoRepository;
import com.stock.data.service.TradingCalendarService;
import com.stock.model.dto.LstmPredictionResultDto;
import com.stock.model.dto.PredictionOperationResponse;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelPredictionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模型预测持久化队列服务。
 *
 * 负责预测批次认领、全市场推理、进度持久化、失败重试和评估队列衔接。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
public class PredictionExecutionQueueService {

    private static final int PREDICTION_BATCH_SIZE = 100;
    private static final String EVALUATION_WAIT_PREDICTION = "PREDICTION_NOT_COMPLETED";
    private static final String EVALUATION_WAIT_MARKET_DATA = "MARKET_DATA_COVERAGE_LOW";

    private final LstmInferenceService inferenceService;
    private final StockInfoRepository stockInfoRepository;
    private final TradingCalendarService tradingCalendarService;
    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;
    private final ModelPredictionQueryService queryService;
    private final PredictionEvaluationService evaluationService;
    private final PredictionEvaluationQueueService evaluationQueueService;
    private final PredictionBatchClaimService claimService;
    private final Executor queueExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 创建模型预测队列服务。
     *
     * @param inferenceService LSTM 推理服务
     * @param stockInfoRepository 股票基础信息仓库
     * @param tradingCalendarService 交易日历服务
     * @param batchRepository 预测批次仓库
     * @param predictionRepository 预测明细仓库
     * @param queryService 预测查询服务
     * @param evaluationService 预测评估服务
     * @param evaluationQueueService 预测评估队列服务
     * @param claimService 预测批次事务认领服务
     * @param queueExecutor 预测队列执行器
     */
    public PredictionExecutionQueueService(
            LstmInferenceService inferenceService,
            StockInfoRepository stockInfoRepository,
            TradingCalendarService tradingCalendarService,
            ModelPredictionBatchRepository batchRepository,
            ModelPredictionRepository predictionRepository,
            ModelPredictionQueryService queryService,
            PredictionEvaluationService evaluationService,
            PredictionEvaluationQueueService evaluationQueueService,
            PredictionBatchClaimService claimService,
            @Qualifier("modelPredictionQueueExecutor") Executor queueExecutor) {
        this.inferenceService = inferenceService;
        this.stockInfoRepository = stockInfoRepository;
        this.tradingCalendarService = tradingCalendarService;
        this.batchRepository = batchRepository;
        this.predictionRepository = predictionRepository;
        this.queryService = queryService;
        this.evaluationService = evaluationService;
        this.evaluationQueueService = evaluationQueueService;
        this.claimService = claimService;
        this.queueExecutor = queueExecutor;
    }

    /**
     * 应用重启后将中断的预测批次恢复为排队状态。
     */
    public void recoverInterruptedBatches() {
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
    }

    /**
     * 清理预测失败批次的残留结果并重新加入预测队列。
     *
     * @param batchId 预测批次标识
     * @return 重新入队结果
     */
    public synchronized PredictionOperationResponse retry(String batchId) {
        ModelPredictionBatchDocument batch = queryService.resolveBatch(batchId);
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
        evaluationService.resetEvaluationSummary(batch, "预测重试尚未完成");
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        submitWorker();
        log.info("预测失败批次已重新入队: batchId={}", batch.getId());
        return PredictionOperationResponse.builder()
                .batchId(batch.getId()).status("QUEUED").message("预测失败批次已重新进入队列").build();
    }

    /**
     * 异步提交预测队列 Worker。
     *
     * @return Worker 完成信号
     */
    public CompletableFuture<Void> submitWorker() {
        return CompletableFuture.runAsync(this::consumeQueue, queueExecutor);
    }

    /**
     * 等待本次预测队列 Worker 完成并传播业务异常。
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

    private void consumeQueue() {
        if (!running.compareAndSet(false, true)) {
            log.info("预测队列已有 Worker 正在消费，本次触发仅保留持久化队列数据");
            return;
        }
        try {
            ModelPredictionBatchDocument batch;
            while ((batch = claimNext()) != null) {
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
            running.set(false);
            if (batchRepository.existsByStatus("QUEUED")) {
                submitWorker();
            }
        }
    }

    private ModelPredictionBatchDocument claimNext() {
        return claimService.claimNextPrediction();
    }

    private List<String> resolveQueuedBatchStockCodes(ModelPredictionBatchDocument batch) {
        if (batch.getRerunSourceBatchId() == null || batch.getRerunSourceBatchId().isBlank()) {
            return loadPredictionStockCodes();
        }
        List<String> stockCodes = predictionRepository.findByBatchId(batch.getRerunSourceBatchId()).stream()
                .map(ModelPredictionDocument::getStockCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct().sorted().toList();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("历史预测重算来源批次没有可用股票明细: " + batch.getRerunSourceBatchId());
        }
        return stockCodes;
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
                    (currentCodes, results) -> persistChunk(batch, stockCodes.size(), currentCodes, results,
                            targetDateCache, processedCount, successCount, failedCount, startedNanos));
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
            evaluationService.refreshAbility(batch, predictionRepository.findByBatchId(batch.getId()));
            ModelPredictionBatchDocument completed = batchRepository.findById(batch.getId()).orElse(batch);
            if (evaluationQueueService.enqueue(completed)) {
                evaluationQueueService.submitWorker();
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

    private void persistChunk(ModelPredictionBatchDocument batch, int totalStocks, List<String> currentCodes,
                              Map<String, LstmPredictionResultDto> results,
                              Map<LocalDate, TradeDateResolution> targetDateCache,
                              int[] processedCount, int[] successCount, int[] failedCount, long startedNanos) {
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
                        batch.getId(), result.getStockCode(), batch.getBaseTradeDate(), result.getBaseTradeDate());
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
        batch.setCoverageRate(rate(successCount[0], totalStocks));
        batch.setProgressMessage("已完成 " + processedCount[0] + "/" + totalStocks + " 支股票预测");
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        log.info("全市场模型预测进度: batchId={}, processed={}/{}, success={}, failed={}, elapsedSeconds={}",
                batch.getId(), processedCount[0], totalStocks, successCount[0], failedCount[0],
                elapsedSeconds(startedNanos));
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

    private TradeDateResolution resolveNextTradeDate(LocalDate baseTradeDate) {
        return new TradeDateResolution(tradingCalendarService.nextTradingDay(baseTradeDate), false);
    }

    private Double rate(int numerator, int denominator) {
        return denominator <= 0 ? 0D : numerator / (double) denominator;
    }

    private long elapsedSeconds(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000_000L);
    }

    private record TradeDateResolution(LocalDate tradeDate, boolean calendarFallback) {
    }
}
// AI_GENERATE_END---
