// AI_GENERATE_START------------------
package com.stock.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.market.service.MarketDataQueryService;
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelOperationResponse;
import com.stock.model.dto.ModelTrainingRequest;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.entity.ModelTrainingRunDocument;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import com.stock.model.repository.ModelTrainingRunRepository;
import com.stock.model.algorithm.LstmArchitectureType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 模型运维服务。
 * 负责训练快照准备、异步训练、停止控制和候选模型质量门禁。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Slf4j
@Service
public class ModelOperationsService {

    private static final List<String> RUNNING_STATUSES = List.of("QUEUED", "RUNNING", "STOPPING");
    private static final int DEFAULT_TRAIN_DAYS = 2000;
    private final LstmTrainingConfig trainingConfig;
    private final LstmDataQualityConfig dataQualityConfig;
    private final MarketDataQueryService marketDataQueryService;
    private final LstmTrainerService lstmTrainerService;
    private final TrainingDatasetSnapshotService snapshotService;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository activationRepository;
    private final ModelTrainingRunRepository trainingRunRepository;
    private final ObjectMapper objectMapper;
    private final ModelOperationAuditService auditService;
    private final ModelPromotionPolicy promotionPolicy;
    private final ModelActivationService activationService;
    private final TemporalModelEnsembleService temporalModelEnsembleService;
    private final Executor applicationTaskExecutor;

    /**
     * 创建模型运维服务。
     *
     * @param trainingConfig LSTM 训练配置
     * @param dataQualityConfig LSTM 数据质量配置
     * @param marketDataQueryService 市场数据查询服务
     * @param lstmTrainerService LSTM 训练服务
     * @param snapshotService 训练数据快照服务
     * @param lstmModelRepository LSTM 模型仓库
     * @param activationRepository 模型激活指针仓库
     * @param trainingRunRepository 训练运行仓库
     * @param objectMapper JSON 序列化器
     * @param auditService 模型操作审计服务
     * @param promotionPolicy 模型自动晋级策略
     * @param activationService 基础模型自动激活服务
     * @param temporalModelEnsembleService MASTER 候选和融合服务
     * @param applicationTaskExecutor 应用异步执行器
     */
    public ModelOperationsService(LstmTrainingConfig trainingConfig,
                                  LstmDataQualityConfig dataQualityConfig,
                                  MarketDataQueryService marketDataQueryService,
                                  LstmTrainerService lstmTrainerService,
                                  TrainingDatasetSnapshotService snapshotService,
                                  LstmModelRepository lstmModelRepository,
                                  ModelActivationRepository activationRepository,
                                  ModelTrainingRunRepository trainingRunRepository,
                                  ObjectMapper objectMapper,
                                  ModelOperationAuditService auditService,
                                  ModelPromotionPolicy promotionPolicy,
                                  ModelActivationService activationService,
                                  TemporalModelEnsembleService temporalModelEnsembleService,
                                  @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.trainingConfig = trainingConfig;
        this.dataQualityConfig = dataQualityConfig;
        this.marketDataQueryService = marketDataQueryService;
        this.lstmTrainerService = lstmTrainerService;
        this.snapshotService = snapshotService;
        this.lstmModelRepository = lstmModelRepository;
        this.activationRepository = activationRepository;
        this.trainingRunRepository = trainingRunRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.promotionPolicy = promotionPolicy;
        this.activationService = activationService;
        this.temporalModelEnsembleService = temporalModelEnsembleService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 提交受控异步训练任务。
     *
     * @param request 训练请求
     * @return 训练任务提交结果
     */
    public synchronized ModelOperationResponse startTraining(
            ModelTrainingRequest request) {
        ModelTrainingRequest safeRequest = request == null
                ? new ModelTrainingRequest() : request;
        return submitTraining(safeRequest, "WEB_CONSOLE",
                normalizeOperator(safeRequest.getOperatorName()), true);
    }

    /**
     * 预先构建可复用的 LSTM 训练数据快照。
     *
     * @param request 快照构建参数
     * @return 快照构建结果
     */
    public synchronized ModelOperationResponse prepareTrainingSnapshot(
            ModelTrainingRequest request) {
        ModelTrainingRequest safeRequest = request == null
                ? new ModelTrainingRequest() : request;
        int days = safeRequest.getDays() == null ? DEFAULT_TRAIN_DAYS : safeRequest.getDays();
        validateRange("训练天数", days, 90, 2000);
        List<String> stockCodes = marketDataQueryService.findTradableStockCodes().stream()
                .filter(code -> dataQualityConfig.getSkipTrainingCodes() == null
                        || !dataQualityConfig.getSkipTrainingCodes().contains(code))
                .toList();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("股票基础数据为空，无法构建训练数据快照");
        }
        log.info("构建训练数据快照不受训练开关和训练时间窗口限制: modelName={}, days={}, stocks={}",
                lstmTrainerService.getModelName(), days, stockCodes.size());
        TrainingDatasetSnapshotDocument snapshot = snapshotService.buildSnapshot(
                lstmTrainerService.getModelName(), stockCodes, days);
        auditService.record(lstmTrainerService.getModelName(), "SNAPSHOT_BUILT",
                normalizeOperator(safeRequest.getOperatorName()), null, snapshot.getId(), true,
                "训练数据快照构建完成");
        return ModelOperationResponse.builder()
                .operationType("TRAINING_DATASET_SNAPSHOT")
                .operationId(snapshot.getId())
                .message("训练数据快照已构建").build();
    }

    /**
     * 请求停止指定的训练任务。
     *
     * @param runId 训练运行 ID
     * @param operatorName 操作人名称
     * @return 停止请求结果
     */
    public synchronized ModelOperationResponse stopTraining(String runId, String operatorName) {
        ModelTrainingRunDocument run = trainingRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("训练任务不存在"));
        if (!RUNNING_STATUSES.contains(run.getStatus())) {
            throw new IllegalStateException("当前训练任务不可停止，状态为: " + run.getStatus());
        }
        String operator = normalizeOperator(operatorName);
        if ("QUEUED".equals(run.getStatus())) {
            markTrainingCancelled(run, "训练任务在排队阶段被停止");
        } else {
            run.setStatus("STOPPING");
            run.setProgressMessage("已收到停止请求，等待当前训练检查点结束");
            run.setLastProgressAt(LocalDateTime.now());
            trainingRunRepository.save(run);
        }
        auditService.record(run.getModelName(), "TRAIN_STOP_REQUESTED", operator, null, null, true, "已请求停止训练任务");
        log.info("已请求停止模型训练: runId={}, status={}, operator={}", runId, run.getStatus(), operator);
        return ModelOperationResponse.builder().operationType("TRAIN_STOP").operationId(runId).message("已提交停止训练请求").build();
    }

    /**
     * 由统一调度中心提交共享 LSTM 训练任务。
     *
     * @return 定时训练提交或跳过结果
     */
    public synchronized ModelOperationResponse startScheduledTraining() {
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setOperatorName("job-scheduler");
        LstmArchitectureType baselineArchitecture = trainingConfig.getArchitectureType()
                == LstmArchitectureType.MASTER_TRANSFORMER
                ? LstmArchitectureType.RANKING_LSTM : trainingConfig.getArchitectureType();
        request.setArchitectureType(baselineArchitecture.name());
        return submitTraining(request, "SCHEDULER", "job-scheduler", false);
    }

    private ModelOperationResponse submitTraining(
            ModelTrainingRequest safeRequest,
            String triggerSource,
            String operatorName,
            boolean throwWhenRejected) {
        validateTrainingParameters(safeRequest);
        String modelName = lstmTrainerService.getModelName();
        TrainingCheckResult checkResult = evaluateTrainingSubmission(hasRunningTraining(modelName));
        if (!checkResult.allowed()) {
            auditService.record(modelName, "TRAIN_REJECTED", operatorName, null, null, false, checkResult.message());
            if (throwWhenRejected) {
                throw new IllegalStateException(checkResult.message());
            }
            log.info("定时 LSTM 训练提交条件不满足: reason={}", checkResult.message());
            return ModelOperationResponse.builder()
                    .operationType("TRAIN_SKIPPED")
                    .message(checkResult.message())
                    .build();
        }

        int days = safeRequest.getDays() == null ? DEFAULT_TRAIN_DAYS : safeRequest.getDays();
        int epochs = safeRequest.getEpochs() == null ? trainingConfig.getEpochs() : safeRequest.getEpochs();
        int batchSize = safeRequest.getBatchSize() == null
                ? trainingConfig.getBatchSize() : safeRequest.getBatchSize();
        double learningRate = safeRequest.getLearningRate() == null
                ? trainingConfig.getLearningRate() : safeRequest.getLearningRate();
        LstmArchitectureType architectureType = resolveArchitectureType(safeRequest.getArchitectureType());
        validateEffectiveTrainingParameters(days, epochs, batchSize, learningRate);

        ModelTrainingRunDocument run = new ModelTrainingRunDocument();
        run.setModelName(modelName);
        run.setStatus("QUEUED");
        run.setTriggerSource(triggerSource);
        run.setTriggeredBy(operatorName);
        run.setRequestedConfigJson(writeJson(trainingRequestMap(safeRequest)));
        run.setEffectiveConfigJson(writeJson(
                effectiveConfigMap(days, epochs, batchSize, learningRate, architectureType)));
        run.setGateResultJson(writeJson(Map.of("allowed", true, "message", checkResult.message())));
        run.setProgressStage("QUEUED");
        run.setProgressMessage("训练任务已进入队列");
        run.setProgressPercent(0);
        run.setCurrentEpoch(0);
        run.setTotalEpochs(epochs);
        run.setElapsedSeconds(0L);
        run.setLastProgressAt(LocalDateTime.now());
        run.setCreatedAt(LocalDateTime.now());
        run.setDatasetSnapshotId(null);
        ModelTrainingRunDocument saved = trainingRunRepository.save(run);
        auditService.record(modelName, "TRAIN_SUBMITTED", operatorName, null, null, true, "训练任务已进入队列");

        final int effectiveDays = days;
        final int effectiveEpochs = epochs;
        final int effectiveBatchSize = batchSize;
        final double effectiveLearningRate = learningRate;
        final LstmArchitectureType effectiveArchitectureType = architectureType;
        applicationTaskExecutor.execute(() -> executeTraining(
                saved.getId(), effectiveDays, effectiveEpochs, effectiveBatchSize,
                effectiveLearningRate, effectiveArchitectureType));
        return ModelOperationResponse.builder()
                .operationType("TRAIN")
                .operationId(saved.getId())
                .message("训练任务已提交")
                .build();
    }

    private void executeTraining(
            String runId, int days, int epochs, int batchSize, double learningRate,
            LstmArchitectureType architectureType) {
        ModelTrainingRunDocument run = trainingRunRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("模型训练运行记录不存在: runId={}", runId);
            return;
        }
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setProgressStage("PREPARING_DATA");
        run.setProgressMessage("正在复核训练数据与资源");
        run.setProgressPercent(1);
        run.setCurrentEpoch(0);
        run.setTotalEpochs(epochs);
        run.setElapsedSeconds(0L);
        run.setLastProgressAt(LocalDateTime.now());
        trainingRunRepository.save(run);
        try {
            TrainingCheckResult executionCheck = evaluateTrainingEnvironment();
            if (!executionCheck.allowed()) {
                run.setGateResultJson(writeJson(Map.of(
                        "allowed", false,
                        "phase", "EXECUTION",
                        "message", executionCheck.message())));
                throw new IllegalStateException(executionCheck.message());
            }
            List<String> stockCodes = marketDataQueryService.findTradableStockCodes().stream()
                    .filter(code -> dataQualityConfig.getSkipTrainingCodes() == null
                            || !dataQualityConfig.getSkipTrainingCodes().contains(code))
                    .toList();
            checkTrainingStopRequested(run.getId());
            LstmTrainerService.TrainingResult result = lstmTrainerService.trainSharedModel(
                    stockCodes, days, epochs, batchSize, learningRate,
                    progress -> updateTrainingProgress(run, progress),
                    run.getDatasetSnapshotId(), architectureType);
            if (result != null) {
                run.setTrainedStocks(result.getTrainedStocks());
                run.setTrainSamples(result.getTrainSamples());
                run.setValSamples(result.getValSamples());
                run.setTrainLoss(result.getTrainLoss());
                run.setValLoss(result.getValLoss());
                run.setDatasetSnapshotId(result.getSnapshotId());
            }
            if (result == null || !result.isSuccess()) {
                throw new IllegalStateException(result == null ? "训练无返回结果" : result.getMessage());
            }
            String candidateVersionId = parseModelVersionId(result.getModelPath());
            run.setProgressStage("VALIDATING_MODEL");
            run.setProgressMessage("正在校验候选模型完整性和质量");
            run.setProgressPercent(98);
            run.setEstimatedRemainingSeconds(0L);
            run.setLastProgressAt(LocalDateTime.now());
            trainingRunRepository.save(run);
            LstmModelDocument candidate = lstmModelRepository.findById(candidateVersionId)
                    .orElseThrow(() -> new IllegalStateException("候选模型版本不存在"));
            run.setCandidateModelVersionId(candidateVersionId);
            try {
                lstmTrainerService.validateModelVersion(candidateVersionId);
                double candidateScore = validateCandidateQuality(result);
                handleCandidate(candidate, architectureType, candidateScore, run.getTriggeredBy());
            } catch (RuntimeException exception) {
                candidate.setStatus("RETIRED");
                lstmModelRepository.save(candidate);
                throw exception;
            }

            run.setStatus("SUCCEEDED");
            run.setProgressStage("COMPLETED");
            run.setProgressMessage("训练完成，候选模型已完成自动评估");
            run.setProgressPercent(100);
            run.setEstimatedRemainingSeconds(0L);
            run.setLastProgressAt(LocalDateTime.now());
            auditService.record(run.getModelName(), "TRAIN_COMPLETED", run.getTriggeredBy(),
                    null, candidateVersionId, true, "候选模型训练和自动评估完成");
        } catch (LstmTrainerService.TrainingCancelledException exception) {
            log.info("受控模型训练已停止: runId={}, reason={}", runId, exception.getMessage());
            run.setStatus("CANCELLED");
            run.setProgressStage("CANCELLED");
            run.setProgressMessage(exception.getMessage());
            run.setEstimatedRemainingSeconds(0L);
            run.setLastProgressAt(LocalDateTime.now());
            auditService.record(run.getModelName(), "TRAIN_CANCELLED", run.getTriggeredBy(), null, null, true, exception.getMessage());
        } catch (Exception exception) {
            log.error("受控模型训练失败: runId={}", runId, exception);
            run.setStatus("FAILED");
            run.setErrorMessage(limitErrorMessage(exception.getMessage()));
            run.setProgressStage("FAILED");
            run.setProgressMessage("训练失败：" + run.getErrorMessage());
            run.setEstimatedRemainingSeconds(0L);
            run.setLastProgressAt(LocalDateTime.now());
            auditService.record(run.getModelName(), "TRAIN_FAILED", run.getTriggeredBy(),
                    null, null, false, run.getErrorMessage());
        } finally {
            run.setFinishedAt(LocalDateTime.now());
            trainingRunRepository.save(run);
        }
        if ("SUCCEEDED".equals(run.getStatus())
                && "SCHEDULER".equals(run.getTriggerSource())
                && architectureType != LstmArchitectureType.MASTER_TRANSFORMER
                && isActiveCandidate(run.getCandidateModelVersionId())
                && temporalModelEnsembleService.shouldTrainMasterCandidate(
                        run.getCandidateModelVersionId())) {
            submitScheduledMasterTraining(days, epochs, batchSize, learningRate);
        }
    }

    private boolean isActiveCandidate(String candidateVersionId) {
        return candidateVersionId != null
                && lstmModelRepository.findById(candidateVersionId)
                .map(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElse(false);
    }

    private void submitScheduledMasterTraining(
            int days, int epochs, int batchSize, double learningRate) {
        ModelTrainingRequest request = new ModelTrainingRequest();
        request.setDays(days);
        request.setEpochs(epochs);
        request.setBatchSize(batchSize);
        request.setLearningRate(learningRate);
        request.setArchitectureType(LstmArchitectureType.MASTER_TRANSFORMER.name());
        request.setOperatorName("job-scheduler");
        synchronized (this) {
            submitTraining(request, "SCHEDULER_CHAIN", "job-scheduler", false);
        }
    }

    private void updateTrainingProgress(ModelTrainingRunDocument run,
                                        LstmTrainerService.TrainingProgress progress) {
        checkTrainingStopRequested(run.getId());
        run.setProgressStage(progress.getStage());
        run.setProgressMessage(progress.getMessage());
        run.setProgressPercent(Math.max(0, Math.min(100, progress.getProgressPercent())));
        run.setCurrentEpoch(progress.getCurrentEpoch());
        run.setTotalEpochs(progress.getTotalEpochs());
        run.setElapsedSeconds(progress.getElapsedSeconds());
        run.setEstimatedRemainingSeconds(progress.getEstimatedRemainingSeconds());
        run.setLastProgressAt(LocalDateTime.now());
        if (progress.getTrainedStocks() != null) {
            run.setTrainedStocks(progress.getTrainedStocks());
        }
        if (progress.getTrainSamples() != null) {
            run.setTrainSamples(progress.getTrainSamples());
        }
        if (progress.getValSamples() != null) {
            run.setValSamples(progress.getValSamples());
        }
        if (progress.getTrainLoss() != null) {
            run.setTrainLoss(progress.getTrainLoss());
        }
        if (progress.getValLoss() != null) {
            run.setValLoss(progress.getValLoss());
        }
        if (progress.getSnapshotId() != null && !progress.getSnapshotId().isBlank()) {
            run.setDatasetSnapshotId(progress.getSnapshotId());
        }
        trainingRunRepository.save(run);
    }

    private void checkTrainingStopRequested(String runId) {
        ModelTrainingRunDocument current = trainingRunRepository.findById(runId).orElse(null);
        if (current != null && "STOPPING".equals(current.getStatus())) {
            throw new LstmTrainerService.TrainingCancelledException("训练已按请求停止");
        }
    }

    private void markTrainingCancelled(ModelTrainingRunDocument run, String message) {
        run.setStatus("CANCELLED");
        run.setProgressStage("CANCELLED");
        run.setProgressMessage(message);
        run.setProgressPercent(run.getProgressPercent() == null ? 0 : run.getProgressPercent());
        run.setEstimatedRemainingSeconds(0L);
        run.setFinishedAt(LocalDateTime.now());
        run.setLastProgressAt(LocalDateTime.now());
        trainingRunRepository.save(run);
    }

    private TrainingCheckResult evaluateTrainingSubmission(boolean trainingRunning) {
        if (trainingRunning) {
            return new TrainingCheckResult(false, "当前模型已有排队或运行中的训练任务");
        }
        return evaluateTrainingEnvironment();
    }

    private TrainingCheckResult evaluateTrainingEnvironment() {
        if (marketDataQueryService.countTradableStocks() <= 0) {
            return new TrainingCheckResult(false, "股票基础数据为空，无法训练");
        }
        long minimumMemoryBytes = Math.max(1L, dataQualityConfig.getMinimumAvailableMemoryMb())
                * 1024L * 1024L;
        if (availableJvmMemory() < minimumMemoryBytes) {
            return new TrainingCheckResult(false, "JVM 可用内存低于 "
                    + dataQualityConfig.getMinimumAvailableMemoryMb() + "MB，拒绝启动训练");
        }
        return new TrainingCheckResult(true, "训练提交条件校验通过");
    }

    private boolean hasRunningTraining(String modelName) {
        return trainingRunRepository.existsByModelNameAndStatusIn(modelName, RUNNING_STATUSES);
    }

    private void validateTrainingParameters(ModelTrainingRequest request) {
        validateRange("训练天数", request.getDays(), 90, 2000);
        validateRange("训练轮次", request.getEpochs(), 1, 500);
        validateRange("批次大小", request.getBatchSize(), 8, 512);
        if (request.getLearningRate() != null
                && (request.getLearningRate() < 0.000001D || request.getLearningRate() > 0.1D)) {
            throw new IllegalArgumentException("学习率必须在 0.000001 到 0.1 之间");
        }
    }

    private void validateEffectiveTrainingParameters(
            int days, int epochs, int batchSize, double learningRate) {
        validateRange("训练天数", days, 90, 2000);
        validateRange("训练轮次", epochs, 1, 500);
        validateRange("批次大小", batchSize, 8, 512);
        if (!Double.isFinite(learningRate) || learningRate < 0.000001D || learningRate > 0.1D) {
            throw new IllegalArgumentException("学习率必须在 0.000001 到 0.1 之间");
        }
    }

    private double validateCandidateQuality(LstmTrainerService.TrainingResult result) {
        if (result.getTrainedStocks() < dataQualityConfig.getMinimumTrainingStockCount()) {
            throw new IllegalStateException("候选模型有效股票数量未达到质量阈值");
        }
        if (result.getTrainSamples() < dataQualityConfig.getMinimumTrainSamples()
                || result.getValSamples() < dataQualityConfig.getMinimumValidationSamples()) {
            throw new IllegalStateException("候选模型训练或验证样本数量未达到质量阈值");
        }
        if (!Double.isFinite(result.getTrainLoss()) || !Double.isFinite(result.getValLoss())) {
            throw new IllegalStateException("候选模型损失不是有效数值");
        }
        if (result.getValLoss() > dataQualityConfig.getMaximumValidationLoss()) {
            throw new IllegalStateException("候选模型验证损失超过阈值: "
                    + result.getValLoss() + " > " + dataQualityConfig.getMaximumValidationLoss());
        }
        if (result.isOutputCollapsed()) {
            throw new IllegalStateException("候选模型输出发生塌缩");
        }
        double walkForwardMeanRankIc = requireFiniteMetric(
                "Walk-Forward 平均 Rank IC", result.getWalkForwardMeanRankIc());
        double walkForwardTopDecileNetReturn = requireFiniteMetric(
                "Walk-Forward Top 10% 成本后收益", result.getWalkForwardTopDecileNetReturn());
        double maximumDrawdown = requireFiniteMetric(
                "Walk-Forward 最大回撤", result.getMaximumDrawdown());
        promotionPolicy.requirePerformanceHardGate(walkForwardTopDecileNetReturn, maximumDrawdown);
        return promotionPolicy.qualityScore(
                walkForwardMeanRankIc, walkForwardTopDecileNetReturn, maximumDrawdown);
    }

    private double requireFiniteMetric(String metricName, Double value) {
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalStateException("候选模型" + metricName + "不是有效数值: " + value);
        }
        return value;
    }

    private void handleCandidate(
            LstmModelDocument candidate, LstmArchitectureType architectureType,
            double candidateScore, String operatorName) {
        candidate.setStatus("CANDIDATE");
        lstmModelRepository.save(candidate);
        if (architectureType == LstmArchitectureType.MASTER_TRANSFORMER) {
            temporalModelEnsembleService.registerMasterCandidate(candidate.getId());
            auditService.record(candidate.getModelName(), "MASTER_CANDIDATE_REGISTERED", operatorName,
                    null, candidate.getId(), true, "MASTER 候选已自动进入影子评估");
            return;
        }
        Double activeScore = activeQualityScore(candidate);
        if (promotionPolicy.shouldPromote(candidateScore, activeScore)) {
            activationService.activateCandidate(candidate.getId(), operatorName);
            return;
        }
        candidate.setStatus("RETIRED");
        lstmModelRepository.save(candidate);
        auditService.record(candidate.getModelName(), "AUTO_PROMOTE_SKIPPED", operatorName,
                null, candidate.getId(), true,
                "候选综合质量分未达到自动晋级要求: candidate=" + candidateScore
                        + ", active=" + activeScore);
    }

    private Double activeQualityScore(LstmModelDocument candidate) {
        ModelActivationDocument activation = activationRepository.findByModelName(candidate.getModelName())
                .orElse(null);
        if (activation == null || activation.getActiveModelVersionId() == null
                || activation.getActiveModelVersionId().isBlank()) {
            return null;
        }
        LstmModelDocument active = lstmModelRepository.findById(activation.getActiveModelVersionId()).orElse(null);
        if (active == null || active.getMetricsJson() == null || active.getMetricsJson().isBlank()) {
            return null;
        }
        try {
            JsonNode metrics = objectMapper.readTree(active.getMetricsJson());
            if (metrics == null || !metrics.isObject()) {
                return null;
            }
            Double activeRankIc = comparableMetric(metrics, "walkForwardMeanRankIc");
            Double activeNetReturn = comparableMetric(metrics, "walkForwardTopDecileNetReturn");
            Double activeDrawdown = comparableMetric(metrics, "walkForwardMaximumDrawdown");
            if (activeRankIc == null || activeNetReturn == null || activeDrawdown == null) {
                return null;
            }
            return promotionPolicy.qualityScore(activeRankIc, activeNetReturn, activeDrawdown);
        } catch (JsonProcessingException | RuntimeException exception) {
            log.warn("当前生产模型指标无法解析，按无可比较生产评分处理: modelId={}", active.getId());
            return null;
        }
    }

    private Double comparableMetric(JsonNode metrics, String fieldName) {
        if (!metrics.has(fieldName) || metrics.path(fieldName).isNull()) {
            return null;
        }
        JsonNode valueNode = metrics.path(fieldName);
        if (!valueNode.isNumber() || !Double.isFinite(valueNode.doubleValue())) {
            throw new IllegalStateException("当前激活模型指标无效: " + fieldName);
        }
        return valueNode.doubleValue();
    }

    private void validateRange(String name, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(name + "必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private int configInt(ModelTrainingRunDocument run, String key, int fallback) {
        try {
            return objectMapper.readTree(run.getEffectiveConfigJson()).path(key).asInt(fallback);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private double configDouble(ModelTrainingRunDocument run, String key, double fallback) {
        try {
            return objectMapper.readTree(run.getEffectiveConfigJson()).path(key).asDouble(fallback);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private Map<String, Object> trainingRequestMap(ModelTrainingRequest request) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("days", request.getDays());
        values.put("epochs", request.getEpochs());
        values.put("batchSize", request.getBatchSize());
        values.put("learningRate", request.getLearningRate());
        values.put("architectureType", request.getArchitectureType());
        values.put("operatorName", normalizeOperator(request.getOperatorName()));
        return values;
    }

    private Map<String, Object> effectiveConfigMap(
            int days, int epochs, int batchSize, double learningRate,
            LstmArchitectureType architectureType) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("days", days);
        values.put("epochs", epochs);
        values.put("batchSize", batchSize);
        values.put("learningRate", learningRate);
        values.put("architectureType", architectureType.name());
        values.put("sequenceLength", trainingConfig.getSequenceLength());
        values.put("inputSize", trainingConfig.getInputSize());
        values.put("featureVersion", RankingLstmDataPreprocessor.FEATURE_VERSION);
        return values;
    }

    private LstmArchitectureType resolveArchitectureType(String requested) {
        if (requested == null || requested.isBlank()) {
            return trainingConfig.getArchitectureType();
        }
        try {
            LstmArchitectureType type = LstmArchitectureType.valueOf(requested.trim().toUpperCase());
            if (type == LstmArchitectureType.LEGACY_MULTITASK_LSTM) {
                throw new IllegalArgumentException("不允许通过新训练任务生成 Legacy LSTM");
            }
            return type;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("architectureType 仅支持 RANKING_LSTM、"
                    + "DUAL_ATTENTION_RANKING_LSTM 或 MASTER_TRANSFORMER", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型运维参数序列化失败", exception);
        }
    }

    private String parseModelVersionId(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalStateException("训练结果缺少候选模型版本 ID");
        }
        return modelPath;
    }

    private long availableJvmMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
    }

    private String normalizeOperator(String operatorName) {
        if (operatorName == null || operatorName.isBlank()) {
            return "web-console";
        }
        String normalized = operatorName.trim();
        return normalized.substring(0, Math.min(64, normalized.length()));
    }

    private String limitErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.substring(0, Math.min(1000, message.length()));
    }

    private record TrainingCheckResult(boolean allowed, String message) {
    }

}
// AI_GENERATE_END------------------
