// AI_GENERATE_START-------------------------
package com.stock.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.data.service.MarketDataQueryService;
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelOperationResponse;
import com.stock.model.dto.ModelTrainingRequest;
import com.stock.model.entity.ModelTrainingRunDocument;
import com.stock.model.repository.ModelTrainingRunRepository;
import com.stock.model.algorithm.LstmArchitectureType;
import jakarta.annotation.PostConstruct;
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
 * 负责统一训练任务提交、并发控制、停止控制和定时训练串行衔接。
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
    private final ModelTrainingPipelineService trainingPipelineService;
    private final ModelTrainingRunRepository trainingRunRepository;
    private final ObjectMapper objectMapper;
    private final ModelOperationAuditService auditService;
    private final TemporalModelEnsembleService temporalModelEnsembleService;
    private final Executor applicationTaskExecutor;

    /**
     * 创建模型运维服务。
     *
     * @param trainingConfig LSTM 训练配置
     * @param dataQualityConfig LSTM 数据质量配置
     * @param marketDataQueryService 市场数据查询服务
     * @param lstmTrainerService LSTM 训练服务
     * @param trainingPipelineService 模型训练统一流水线服务
     * @param trainingRunRepository 训练运行仓库
     * @param objectMapper JSON 序列化器
     * @param auditService 模型操作审计服务
     * @param temporalModelEnsembleService MASTER 候选和融合服务
     * @param applicationTaskExecutor 应用异步执行器
     */
    public ModelOperationsService(LstmTrainingConfig trainingConfig,
                                  LstmDataQualityConfig dataQualityConfig,
                                  MarketDataQueryService marketDataQueryService,
                                  LstmTrainerService lstmTrainerService,
                                  ModelTrainingPipelineService trainingPipelineService,
                                  ModelTrainingRunRepository trainingRunRepository,
                                  ObjectMapper objectMapper,
                                  ModelOperationAuditService auditService,
                                  TemporalModelEnsembleService temporalModelEnsembleService,
                                  @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.trainingConfig = trainingConfig;
        this.dataQualityConfig = dataQualityConfig;
        this.marketDataQueryService = marketDataQueryService;
        this.lstmTrainerService = lstmTrainerService;
        this.trainingPipelineService = trainingPipelineService;
        this.trainingRunRepository = trainingRunRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.temporalModelEnsembleService = temporalModelEnsembleService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 应用启动时收敛上次进程中断遗留的训练运行状态。
     * 已完成的数据快照保持不变，后续训练将按数据契约直接复用。
     */
    @PostConstruct
    public void recoverInterruptedTrainingRuns() {
        List<ModelTrainingRunDocument> interrupted = trainingRunRepository
                .findByModelNameAndStatusIn(lstmTrainerService.getModelName(), RUNNING_STATUSES)
                .stream()
                .filter(run -> run.getFinishedAt() == null)
                .toList();
        if (interrupted.isEmpty()) {
            return;
        }
        LocalDateTime recoveredAt = LocalDateTime.now();
        for (ModelTrainingRunDocument run : interrupted) {
            run.setStatus("CANCELLED");
            run.setProgressStage("CANCELLED");
            run.setProgressMessage("应用重启，训练任务已中断；已完成的数据快照可继续复用");
            run.setEstimatedRemainingSeconds(0L);
            run.setFinishedAt(recoveredAt);
            run.setLastProgressAt(recoveredAt);
        }
        trainingRunRepository.saveAll(interrupted);
        log.warn("已恢复应用重启前遗留的训练运行记录: count={}", interrupted.size());
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
        ModelTrainingPipelineService.PipelineExecutionResult result = trainingPipelineService.execute(
                runId, days, epochs, batchSize, learningRate, architectureType);
        ModelTrainingRunDocument run = trainingRunRepository.findById(runId).orElse(null);
        if (run != null
                && result.succeeded()
                && "SCHEDULER".equals(run.getTriggerSource())
                && architectureType != LstmArchitectureType.MASTER_TRANSFORMER
                && result.activeCandidate()
                && temporalModelEnsembleService.shouldTrainMasterCandidate(
                        result.candidateVersionId())) {
            submitScheduledMasterTraining(days, epochs, batchSize, learningRate);
        }
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
        return trainingRunRepository.existsByModelNameAndStatusInAndFinishedAtIsNull(
                modelName, RUNNING_STATUSES);
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

    private void validateRange(String name, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(name + "必须在 " + min + " 到 " + max + " 之间");
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
            throw new IllegalArgumentException(
                    "architectureType 仅支持 RANKING_LSTM 或 MASTER_TRANSFORMER", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型运维参数序列化失败", exception);
        }
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

    private record TrainingCheckResult(boolean allowed, String message) {
    }

}
// AI_GENERATE_END-------------------------
