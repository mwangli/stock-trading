// AI_GENERATE_START ---
package com.stock.modelService.service;

import com.stock.dataCollector.domain.entity.StockInfo;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.persistence.PriceRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.persistence.TradeCalendarRepository;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.dto.ModelPredictionAnalysisDto;
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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final List<String> RUNNING_STATUSES = List.of("QUEUED", "RUNNING");
    private static final List<String> EVALUATABLE_STATUSES = List.of("COMPLETED", "PARTIAL", "EVALUATED");

    private final LstmInferenceService inferenceService;
    private final LstmTrainerService trainerService;
    private final StockInfoRepository stockInfoRepository;
    private final PriceRepository priceRepository;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final ModelActivationRepository activationRepository;
    private final LstmModelRepository modelRepository;
    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;
    private final Executor applicationTaskExecutor;
    private final AtomicBoolean predictionRunning = new AtomicBoolean(false);

    /**
     * 创建模型预测分析服务。
     *
     * @param inferenceService LSTM 推理服务
     * @param trainerService LSTM 训练与模型元数据服务
     * @param stockInfoRepository 股票基础信息仓库
     * @param priceRepository 股票日线仓库
     * @param tradeCalendarRepository 交易日历仓库
     * @param activationRepository 模型激活指针仓库
     * @param modelRepository 模型版本仓库
     * @param batchRepository 预测批次仓库
     * @param predictionRepository 预测明细仓库
     * @param applicationTaskExecutor 应用异步执行器
     */
    public ModelPredictionAnalysisService(
            LstmInferenceService inferenceService,
            LstmTrainerService trainerService,
            StockInfoRepository stockInfoRepository,
            PriceRepository priceRepository,
            TradeCalendarRepository tradeCalendarRepository,
            ModelActivationRepository activationRepository,
            LstmModelRepository modelRepository,
            ModelPredictionBatchRepository batchRepository,
            ModelPredictionRepository predictionRepository,
            @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.inferenceService = inferenceService;
        this.trainerService = trainerService;
        this.stockInfoRepository = stockInfoRepository;
        this.priceRepository = priceRepository;
        this.tradeCalendarRepository = tradeCalendarRepository;
        this.activationRepository = activationRepository;
        this.modelRepository = modelRepository;
        this.batchRepository = batchRepository;
        this.predictionRepository = predictionRepository;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 应用重启后清理遗留的运行中预测批次。
     *
     * 异步预测线程不会跨进程继续运行，因此遗留批次必须显式结束，避免永久占用并发门禁。
     */
    @PostConstruct
    public void recoverInterruptedPredictionBatches() {
        for (ModelPredictionBatchDocument batch : batchRepository.findByStatusIn(RUNNING_STATUSES)) {
            batch.setStatus("FAILED");
            batch.setErrorMessage("应用重启，原预测任务已中断");
            batch.setProgressMessage("预测任务已中断");
            batch.setFinishedAt(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            log.warn("恢复遗留预测批次: batchId={}, previousStatus=RUNNING", batch.getId());
        }
    }

    /**
     * 使用当前激活模型异步发起一轮全市场预测。
     *
     * @param request 预测操作请求
     * @return 已创建或复用的预测批次
     */
    public ModelPredictionAnalysisDto.OperationResponse startPrediction(
            ModelPredictionAnalysisDto.RunPredictionRequest request) {
        return submitPrediction(request, false);
    }

    /**
     * 使用当前激活模型同步执行每日全市场预测。
     *
     * 调度任务在当前 Quartz 线程内完成预测，使任务运行记录与批次最终状态保持一致。
     *
     * @return 已完成或复用的预测批次
     */
    public ModelPredictionAnalysisDto.OperationResponse startScheduledPrediction() {
        return submitPrediction(new ModelPredictionAnalysisDto.RunPredictionRequest("scheduler"), true);
    }

    private ModelPredictionAnalysisDto.OperationResponse submitPrediction(
            ModelPredictionAnalysisDto.RunPredictionRequest request, boolean scheduled) {
        if (!predictionRunning.compareAndSet(false, true)) {
            throw new IllegalStateException("当前已有模型预测批次正在运行");
        }
        boolean executionStarted = false;
        try {
            if (batchRepository.existsByStatusIn(RUNNING_STATUSES)) {
                throw new IllegalStateException("当前已有模型预测批次正在运行");
            }
            String modelName = trainerService.getModelName();
            ModelActivationDocument activation = activationRepository.findByModelName(modelName)
                    .orElseThrow(() -> new IllegalStateException("当前没有已激活模型"));
            LstmModelDocument model = modelRepository.findById(activation.getActiveModelVersionId())
                    .orElseThrow(() -> new IllegalStateException("当前激活模型版本不存在"));
            trainerService.validateModelVersion(model.getId());
            LocalDate baseTradeDate = priceRepository.findTopByOrderByDateDesc()
                    .map(StockPrice::getDate)
                    .orElseThrow(() -> new IllegalStateException("没有可用于预测的日线行情"));
            ModelPredictionBatchDocument existing = batchRepository
                    .findFirstByModelIdAndBaseTradeDateOrderByCreatedAtDesc(model.getId(), baseTradeDate);
            if (existing != null) {
                if (!List.of("FAILED", "CANCELLED").contains(existing.getStatus())) {
                    return ModelPredictionAnalysisDto.OperationResponse.builder()
                            .batchId(existing.getId())
                            .status(existing.getStatus())
                            .message("当前模型和交易日已存在预测批次，已返回原批次")
                            .build();
                }
                predictionRepository.deleteByBatchId(existing.getId());
                log.info("已清理失败或取消批次的不完整预测明细: batchId={}, status={}",
                        existing.getId(), existing.getStatus());
            }

            List<String> stockCodes = stockInfoRepository.findAllCodes().stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(String::trim)
                    .distinct()
                    .sorted()
                    .toList();
            if (stockCodes.isEmpty()) {
                throw new IllegalStateException("股票基础信息为空，无法执行全市场预测");
            }

            LocalDateTime now = LocalDateTime.now();
            TradeDateResolution tradeDateResolution = resolveNextTradeDate(baseTradeDate);
            if (scheduled && tradeDateResolution.tradeDate().isBefore(LocalDate.now())) {
                throw new IllegalStateException("最新日线过旧，无法生成当日预测: baseTradeDate=" + baseTradeDate);
            }
            ModelPredictionBatchDocument batch = new ModelPredictionBatchDocument();
            batch.setModelName(modelName);
            batch.setModelId(model.getId());
            batch.setModelVersion(model.getModelVersion());
            batch.setBaseTradeDate(baseTradeDate);
            batch.setTargetTradeDate(tradeDateResolution.tradeDate());
            batch.setInformationCutoffAt(now);
            batch.setCalendarFallback(tradeDateResolution.calendarFallback());
            batch.setStatus("QUEUED");
            batch.setTotalStocks(stockCodes.size());
            batch.setAbilityLevel("INSUFFICIENT_DATA");
            batch.setAbilityMessage("预测尚未完成");
            batch.setProgressMessage("预测任务已排队");
            batch.setTriggeredBy(normalizeOperator(request == null ? null : request.getOperatorName()));
            batch.setCreatedAt(now);
            batch.setUpdatedAt(now);
            ModelPredictionBatchDocument saved = batchRepository.save(batch);
            if (!scheduled) {
                applicationTaskExecutor.execute(() -> executePrediction(saved.getId(), stockCodes));
                executionStarted = true;
                return ModelPredictionAnalysisDto.OperationResponse.builder()
                        .batchId(saved.getId())
                        .status(saved.getStatus())
                        .message("全市场预测任务已提交")
                        .build();
            }

            executionStarted = true;
            executePrediction(saved.getId(), stockCodes);
            ModelPredictionBatchDocument completed = batchRepository.findById(saved.getId()).orElseThrow();
            if ("FAILED".equals(completed.getStatus())) {
                throw new IllegalStateException(completed.getErrorMessage());
            }
            return ModelPredictionAnalysisDto.OperationResponse.builder()
                    .batchId(completed.getId())
                    .status(completed.getStatus())
                    .message("每日全市场预测执行完成")
                    .build();
        } finally {
            if (!executionStarted) {
                predictionRunning.set(false);
            }
        }
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
        ModelPredictionBatchDocument refreshed = evaluateBatch(batch);
        return ModelPredictionAnalysisDto.OperationResponse.builder()
                .batchId(refreshed.getId())
                .status(refreshed.getStatus())
                .message("实际结果和模型能力指标已刷新")
                .build();
    }

    /**
     * 扫描并评估所有仍包含待回填明细的预测批次。
     *
     * 单个批次失败不会阻断后续批次，任务结束时通过异常汇总失败数量。
     */
    public void evaluatePendingBatches() {
        List<ModelPredictionBatchDocument> batches = batchRepository.findByStatusIn(EVALUATABLE_STATUSES);
        int pendingBatchCount = 0;
        int evaluatedBatchCount = 0;
        int failedBatchCount = 0;
        for (ModelPredictionBatchDocument batch : batches) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("预测评估任务已被中断");
            }
            if (!predictionRepository.existsByBatchIdAndEvaluationStatus(batch.getId(), "PENDING")) {
                continue;
            }
            pendingBatchCount++;
            try {
                evaluateBatch(batch);
                evaluatedBatchCount++;
            } catch (RuntimeException exception) {
                failedBatchCount++;
                log.warn("自动刷新预测批次失败: batchId={}, message={}",
                        batch.getId(), exception.getMessage(), exception);
            }
        }
        log.info("预测批次自动评估完成: scanned={}, pending={}, refreshed={}, failed={}",
                batches.size(), pendingBatchCount, evaluatedBatchCount, failedBatchCount);
        if (failedBatchCount > 0) {
            throw new IllegalStateException("存在 " + failedBatchCount + " 个预测批次评估失败");
        }
    }

    private ModelPredictionBatchDocument evaluateBatch(ModelPredictionBatchDocument batch) {
        if (RUNNING_STATUSES.contains(batch.getStatus())) {
            throw new IllegalStateException("预测批次尚未完成，暂不能评估");
        }
        List<ModelPredictionDocument> predictions = predictionRepository.findByBatchId(batch.getId());
        if (predictions.isEmpty()) {
            throw new IllegalStateException("预测批次没有可评估明细");
        }

        LocalDate latestMarketDate = priceRepository.findTopByOrderByDateDesc()
                .map(StockPrice::getDate)
                .orElse(null);
        Map<LocalDate, List<ModelPredictionDocument>> byTargetDate = predictions.stream()
                .filter(item -> item.getTargetTradeDate() != null)
                .collect(Collectors.groupingBy(ModelPredictionDocument::getTargetTradeDate));
        LocalDateTime evaluatedAt = LocalDateTime.now();
        List<ModelPredictionDocument> changed = new ArrayList<>();
        for (Map.Entry<LocalDate, List<ModelPredictionDocument>> entry : byTargetDate.entrySet()) {
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
                } else if (latestMarketDate != null && !latestMarketDate.isBefore(entry.getKey())) {
                    prediction.setEvaluationStatus("NO_MARKET_DATA");
                    prediction.setEvaluatedAt(evaluatedAt);
                    changed.add(prediction);
                }
            }
        }
        if (!changed.isEmpty()) {
            predictionRepository.saveAll(changed);
        }
        refreshAbility(batch, predictionRepository.findByBatchId(batch.getId()));
        ModelPredictionBatchDocument refreshed = batchRepository.findById(batch.getId()).orElseThrow();
        log.info("预测实际结果刷新完成: batchId={}, evaluated={}, noMarketData={}, ability={}",
                refreshed.getId(), refreshed.getEvaluatedCount(), refreshed.getNoMarketDataCount(),
                refreshed.getAbilityLevel());
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
        Page<ModelPredictionBatchDocument> page = batchRepository.findAll(PageRequest.of(
                safeCurrent - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ModelPredictionAnalysisDto.BatchPageResponse.builder()
                .items(page.getContent().stream().map(this::toBatchItem).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
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

    private void executePrediction(String batchId, List<String> stockCodes) {
        long startedNanos = System.nanoTime();
        ModelPredictionBatchDocument batch = batchRepository.findById(batchId).orElseThrow();
        try {
            batch.setStatus("RUNNING");
            batch.setStartedAt(LocalDateTime.now());
            batch.setProgressMessage("正在执行全市场预测");
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            int successCount = 0;
            int failedCount = 0;
            Map<LocalDate, TradeDateResolution> targetDateCache = new HashMap<>();
            for (int start = 0; start < stockCodes.size(); start += PREDICTION_BATCH_SIZE) {
                if (Thread.currentThread().isInterrupted()) {
                    batch.setStatus("CANCELLED");
                    batch.setProgressMessage("预测任务已停止");
                    batch.setFinishedAt(LocalDateTime.now());
                    batch.setUpdatedAt(LocalDateTime.now());
                    batchRepository.save(batch);
                    log.warn("全市场模型预测已停止: batchId={}, processed={}/{}",
                            batch.getId(), batch.getProcessedStocks(), stockCodes.size());
                    return;
                }
                int end = Math.min(stockCodes.size(), start + PREDICTION_BATCH_SIZE);
                List<String> currentCodes = stockCodes.subList(start, end);
                Map<String, StockInfo> stockInfoMap = stockInfoRepository.findByCodeIn(currentCodes).stream()
                        .collect(Collectors.toMap(StockInfo::getCode, Function.identity(), (left, right) -> left));
                Map<String, LstmPredictionResultDto> results = inferenceService
                        .predictNextBatch(currentCodes, batch.getModelId());
                List<ModelPredictionDocument> documents = new ArrayList<>(results.size());
                LocalDateTime predictedAt = LocalDateTime.now();
                for (LstmPredictionResultDto result : results.values()) {
                    if (result.getBaseTradeDate() == null || result.getLastClosePrice() == null
                            || result.getPredictedChangeRatio() == null) {
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
                successCount += documents.size();
                failedCount += currentCodes.size() - documents.size();
                batch.setProcessedStocks(end);
                batch.setSuccessCount(successCount);
                batch.setFailedCount(failedCount);
                batch.setCoverageRate(rate(successCount, stockCodes.size()));
                batch.setProgressMessage("已完成 " + end + "/" + stockCodes.size() + " 支股票预测");
                batch.setUpdatedAt(LocalDateTime.now());
                batchRepository.save(batch);
                log.info("全市场模型预测进度: batchId={}, processed={}/{}, success={}, failed={}, elapsedSeconds={}",
                        batch.getId(), end, stockCodes.size(), successCount, failedCount,
                        elapsedSeconds(startedNanos));
            }
            batch.setStatus(failedCount == 0 ? "COMPLETED" : "PARTIAL");
            batch.setFinishedAt(LocalDateTime.now());
            batch.setProgressMessage("全市场预测完成");
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            refreshAbility(batch, predictionRepository.findByBatchId(batch.getId()));
            log.info("全市场模型预测完成: batchId={}, modelId={}, success={}, failed={}, elapsedSeconds={}",
                    batch.getId(), batch.getModelId(), successCount, failedCount, elapsedSeconds(startedNanos));
        } catch (Exception exception) {
            batch.setStatus("FAILED");
            batch.setErrorMessage(exception.getMessage());
            batch.setProgressMessage("全市场预测失败");
            batch.setFinishedAt(LocalDateTime.now());
            batch.setUpdatedAt(LocalDateTime.now());
            batchRepository.save(batch);
            log.error("全市场模型预测失败: batchId={}", batchId, exception);
        } finally {
            predictionRunning.set(false);
        }
    }

    private void refreshAbility(ModelPredictionBatchDocument batch, List<ModelPredictionDocument> allPredictions) {
        List<ModelPredictionDocument> evaluated = allPredictions.stream()
                .filter(item -> "EVALUATED".equals(item.getEvaluationStatus()))
                .filter(item -> item.getPredictedReturn() != null && item.getActualReturn() != null)
                .toList();
        int noMarketDataCount = (int) allPredictions.stream()
                .filter(item -> "NO_MARKET_DATA".equals(item.getEvaluationStatus())).count();
        batch.setEvaluatedCount(evaluated.size());
        batch.setNoMarketDataCount(noMarketDataCount);
        batch.setEvaluationRate(rate(evaluated.size(), Math.max(1, batch.getSuccessCount())));
        if (evaluated.isEmpty()) {
            batch.setAbilityLevel("INSUFFICIENT_DATA");
            batch.setIterationRecommended(false);
            batch.setAbilityMessage("目标交易日行情尚未形成，需继续等待实际结果");
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

        batch.setDirectionAccuracy(directionAccuracy);
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
        batch.setAbilityMessage(abilityMessage);
        long pendingCount = allPredictions.stream()
                .filter(item -> "PENDING".equals(item.getEvaluationStatus())).count();
        if (pendingCount == 0 && !RUNNING_STATUSES.contains(batch.getStatus())) {
            batch.setStatus("EVALUATED");
        }
        batch.setUpdatedAt(LocalDateTime.now());
        batchRepository.save(batch);
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
        return tradeCalendarRepository
                .findFirstByTradingDayTrueAndTradeDateAfterOrderByTradeDateAsc(baseTradeDate)
                .map(item -> new TradeDateResolution(item.getTradeDate(), false))
                .orElseGet(() -> {
                    LocalDate candidate = baseTradeDate.plusDays(1);
                    while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY
                            || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        candidate = candidate.plusDays(1);
                    }
                    log.warn("交易日历未覆盖基准日后的日期，按工作日兜底: baseDate={}, targetDate={}",
                            baseTradeDate, candidate);
                    return new TradeDateResolution(candidate, true);
                });
    }

    private ModelPredictionAnalysisDto.BatchItem toBatchItem(ModelPredictionBatchDocument item) {
        return ModelPredictionAnalysisDto.BatchItem.builder()
                .id(item.getId()).modelName(item.getModelName()).modelId(item.getModelId())
                .modelVersion(item.getModelVersion()).baseTradeDate(item.getBaseTradeDate())
                .targetTradeDate(item.getTargetTradeDate()).informationCutoffAt(item.getInformationCutoffAt())
                .calendarFallback(item.isCalendarFallback()).status(item.getStatus())
                .totalStocks(item.getTotalStocks()).processedStocks(item.getProcessedStocks())
                .successCount(item.getSuccessCount()).failedCount(item.getFailedCount())
                .evaluatedCount(item.getEvaluatedCount()).noMarketDataCount(item.getNoMarketDataCount())
                .coverageRate(item.getCoverageRate()).evaluationRate(item.getEvaluationRate())
                .abilityLevel(item.getAbilityLevel()).iterationRecommended(item.isIterationRecommended())
                .abilityMessage(item.getAbilityMessage()).progressMessage(item.getProgressMessage())
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
                .calendarFallback(item.isCalendarFallback())
                .baseClosePrice(item.getBaseClosePrice()).predictedClosePrice(item.getPredictedClosePrice())
                .predictedReturn(item.getPredictedReturn()).directionProbability(item.getDirectionProbability())
                .downsideRisk(item.getDownsideRisk()).actualClosePrice(item.getActualClosePrice())
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
                .directionAccuracy(item.getDirectionAccuracy()).returnMae(item.getReturnMae())
                .returnRmse(item.getReturnRmse()).pearsonIc(item.getPearsonIc())
                .spearmanRankIc(item.getSpearmanRankIc())
                .topDecileAverageReturn(item.getTopDecileAverageReturn())
                .topDecileDirectionAccuracy(item.getTopDecileDirectionAccuracy())
                .highConfidenceAccuracy(item.getHighConfidenceAccuracy())
                .downsideRiskHitRate(item.getDownsideRiskHitRate())
                .abilityLevel(item.getAbilityLevel()).iterationRecommended(item.isIterationRecommended())
                .abilityMessage(item.getAbilityMessage()).build();
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
}
// AI_GENERATE_END ---
