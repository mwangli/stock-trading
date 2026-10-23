// AI_GENERATE_START---------
package com.stock.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.data.repository.StockInfoRepository;
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelOperationResponse;
import com.stock.model.dto.ModelTrainingRequest;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelTrainingRunDocument;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelTrainingRunRepository;
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
    private static final int DEFAULT_TRAIN_DAYS = 1000;
    private final LstmTrainingConfig trainingConfig;
    private final LstmDataQualityConfig dataQualityConfig;
    private final StockInfoRepository stockInfoRepository;
    private final LstmTrainerService lstmTrainerService;
    private final TrainingDatasetSnapshotService snapshotService;
    private final LstmModelRepository lstmModelRepository;
    private final ModelTrainingRunRepository trainingRunRepository;
    private final ObjectMapper objectMapper;
    private final ModelOperationAuditService auditService;
    private final Executor applicationTaskExecutor;

    /**
     * 创建模型运维服务。
     *
     * @param trainingConfig LSTM 训练配置
     * @param dataQualityConfig LSTM 数据质量配置
     * @param stockInfoRepository 股票信息仓库
     * @param lstmTrainerService LSTM 训练服务
     * @param snapshotService 训练数据快照服务
     * @param lstmModelRepository LSTM 模型仓库
     * @param trainingRunRepository 训练运行仓库
     * @param objectMapper JSON 序列化器
     * @param auditService 模型操作审计服务
     * @param applicationTaskExecutor 应用异步执行器
     */
    public ModelOperationsService(LstmTrainingConfig trainingConfig,
                                  LstmDataQualityConfig dataQualityConfig,
                                  StockInfoRepository stockInfoRepository,
                                  LstmTrainerService lstmTrainerService,
                                  TrainingDatasetSnapshotService snapshotService,
                                  LstmModelRepository lstmModelRepository,
                                  ModelTrainingRunRepository trainingRunRepository,
                                  ObjectMapper objectMapper,
                                  ModelOperationAuditService auditService,
                                  @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.trainingConfig = trainingConfig;
        this.dataQualityConfig = dataQualityConfig;
        this.stockInfoRepository = stockInfoRepository;
        this.lstmTrainerService = lstmTrainerService;
        this.snapshotService = snapshotService;
        this.lstmModelRepository = lstmModelRepository;
        this.trainingRunRepository = trainingRunRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
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
        List<String> stockCodes = stockInfoRepository.findAllCodes().stream()
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
        validateEffectiveTrainingParameters(days, epochs, batchSize, learningRate);

        ModelTrainingRunDocument run = new ModelTrainingRunDocument();
        run.setModelName(modelName);
        run.setStatus("QUEUED");
        run.setTriggerSource(triggerSource);
        run.setTriggeredBy(operatorName);
        run.setRequestedConfigJson(writeJson(trainingRequestMap(safeRequest)));
        run.setEffectiveConfigJson(writeJson(effectiveConfigMap(days, epochs, batchSize, learningRate)));
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
        applicationTaskExecutor.execute(() -> executeTraining(
                saved.getId(), effectiveDays, effectiveEpochs, effectiveBatchSize, effectiveLearningRate));
        return ModelOperationResponse.builder()
                .operationType("TRAIN")
                .operationId(saved.getId())
                .message("训练任务已提交")
                .build();
    }

    private void executeTraining(String runId, int days, int epochs, int batchSize, double learningRate) {
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
            List<String> stockCodes = stockInfoRepository.findAllCodes().stream()
                    .filter(code -> dataQualityConfig.getSkipTrainingCodes() == null
                            || !dataQualityConfig.getSkipTrainingCodes().contains(code))
                    .toList();
            checkTrainingStopRequested(run.getId());
            LstmTrainerService.TrainingResult result = lstmTrainerService.trainSharedModel(
                    stockCodes, days, epochs, batchSize, learningRate,
                    progress -> updateTrainingProgress(run, progress),
                    run.getDatasetSnapshotId());
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
                validateCandidateQuality(result);
                candidate.setStatus("READY");
                lstmModelRepository.save(candidate);
            } catch (RuntimeException exception) {
                candidate.setStatus("REJECTED");
                lstmModelRepository.save(candidate);
                throw exception;
            }

            run.setStatus("SUCCEEDED");
            run.setProgressStage("COMPLETED");
            run.setProgressMessage("训练完成，候选模型已通过校验");
            run.setProgressPercent(100);
            run.setEstimatedRemainingSeconds(0L);
            run.setLastProgressAt(LocalDateTime.now());
            auditService.record(run.getModelName(), "TRAIN_COMPLETED", run.getTriggeredBy(),
                    null, candidateVersionId, true, "候选模型训练和兼容性校验通过");
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
        if (stockInfoRepository.countTradable() <= 0) {
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

    private void validateCandidateQuality(LstmTrainerService.TrainingResult result) {
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
        values.put("operatorName", normalizeOperator(request.getOperatorName()));
        return values;
    }

    private Map<String, Object> effectiveConfigMap(
            int days, int epochs, int batchSize, double learningRate) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("days", days);
        values.put("epochs", epochs);
        values.put("batchSize", batchSize);
        values.put("learningRate", learningRate);
        values.put("sequenceLength", trainingConfig.getSequenceLength());
        values.put("inputSize", trainingConfig.getInputSize());
        values.put("featureVersion", LstmDataPreprocessor.FEATURE_VERSION);
        return values;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型运维参数序列化失败", exception);
        }
    }

    private String parseModelVersionId(String modelPath) {
        if (modelPath == null || !modelPath.startsWith("mongo:") || modelPath.length() <= 6) {
            throw new IllegalStateException("训练结果缺少候选模型版本 ID");
        }
        return modelPath.substring(6);
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
// AI_GENERATE_END---------
