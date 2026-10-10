// AI_GENERATE_START --
package com.stock.modelService.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.config.LstmDataQualityConfig;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.dto.ModelOperationsDto;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.entity.ModelActivationDocument;
import com.stock.modelService.domain.entity.ModelOperationAuditDocument;
import com.stock.modelService.domain.entity.ModelTrainingRunDocument;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.persistence.ModelActivationRepository;
import com.stock.modelService.persistence.ModelOperationAuditRepository;
import com.stock.modelService.persistence.ModelTrainingRunRepository;
import com.stock.tradingExecutor.execution.TradingTimeChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 模型运维服务。
 * 负责模型版本查询、训练门禁、异步训练、候选激活、回滚和操作审计。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Slf4j
@Service
public class ModelOperationsService {

    private static final List<String> RUNNING_STATUSES = List.of("QUEUED", "RUNNING");
    private static final int DEFAULT_TRAIN_DAYS = 1000;
    private static final long MIN_AVAILABLE_MEMORY_BYTES = 256L * 1024L * 1024L;
    private static final LocalTime WEEKDAY_TRAINING_START = LocalTime.of(18, 30);
    private static final LocalTime WEEKDAY_TRAINING_END = LocalTime.of(23, 30);

    private final LstmTrainingConfig trainingConfig;
    private final LstmDataQualityConfig dataQualityConfig;
    private final StockInfoRepository stockInfoRepository;
    private final LstmTrainerService lstmTrainerService;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository modelActivationRepository;
    private final ModelTrainingRunRepository trainingRunRepository;
    private final ModelOperationAuditRepository auditRepository;
    private final TradingTimeChecker tradingTimeChecker;
    private final ObjectMapper objectMapper;
    private final Executor applicationTaskExecutor;

    /**
     * 创建模型运维服务。
     *
     * @param trainingConfig LSTM 训练配置
     * @param dataQualityConfig LSTM 数据质量配置
     * @param stockInfoRepository 股票信息仓库
     * @param lstmTrainerService LSTM 训练服务
     * @param lstmModelRepository LSTM 模型仓库
     * @param modelActivationRepository 模型激活指针仓库
     * @param trainingRunRepository 训练运行仓库
     * @param auditRepository 模型操作审计仓库
     * @param tradingTimeChecker 交易时间检查器
     * @param objectMapper JSON 序列化器
     * @param applicationTaskExecutor 应用异步执行器
     */
    public ModelOperationsService(LstmTrainingConfig trainingConfig,
                                  LstmDataQualityConfig dataQualityConfig,
                                  StockInfoRepository stockInfoRepository,
                                  LstmTrainerService lstmTrainerService,
                                  LstmModelRepository lstmModelRepository,
                                  ModelActivationRepository modelActivationRepository,
                                  ModelTrainingRunRepository trainingRunRepository,
                                  ModelOperationAuditRepository auditRepository,
                                  TradingTimeChecker tradingTimeChecker,
                                  ObjectMapper objectMapper,
                                  @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.trainingConfig = trainingConfig;
        this.dataQualityConfig = dataQualityConfig;
        this.stockInfoRepository = stockInfoRepository;
        this.lstmTrainerService = lstmTrainerService;
        this.lstmModelRepository = lstmModelRepository;
        this.modelActivationRepository = modelActivationRepository;
        this.trainingRunRepository = trainingRunRepository;
        this.auditRepository = auditRepository;
        this.tradingTimeChecker = tradingTimeChecker;
        this.objectMapper = objectMapper;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 查询模型运维概览。
     *
     * @return 模型运维概览
     */
    public ModelOperationsDto.OverviewResponse getOverview() {
        String modelName = lstmTrainerService.getModelName();
        ModelActivationDocument activation = modelActivationRepository.findByModelName(modelName).orElse(null);
        boolean trainingRunning = hasRunningTraining(modelName);
        GateResult gateResult = evaluateTrainingGate(modelName, trainingRunning);
        ModelOperationsDto.ModelVersionItem activeVersion = versionById(
                activation == null ? null : activation.getActiveModelVersionId(), activation);
        ModelOperationsDto.ModelVersionItem previousVersion = versionById(
                activation == null ? null : activation.getPreviousModelVersionId(), activation);
        boolean activeHealthy = isActiveVersionHealthy(activation);
        return ModelOperationsDto.OverviewResponse.builder()
                .modelName(modelName)
                .activeVersion(activeVersion)
                .previousVersion(previousVersion)
                .latestTrainingRun(toTrainingRunItem(
                        trainingRunRepository.findTopByModelNameOrderByCreatedAtDesc(modelName)))
                .trainingEnabled(trainingConfig.isTrainingEnabled())
                .trainingAllowed(gateResult.allowed())
                .trainingRunning(trainingRunning)
                .tradingTime(tradingTimeChecker.isTradingTime())
                .activeModelHealthy(activeHealthy)
                .gateMessage(gateResult.message())
                .build();
    }

    /**
     * 分页查询模型版本。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 模型版本分页结果
     */
    public ModelOperationsDto.ModelVersionPageResponse listVersions(int current, int pageSize) {
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String modelName = lstmTrainerService.getModelName();
        Page<LstmModelDocument> page = lstmModelRepository.findByModelName(modelName,
                PageRequest.of(safeCurrent - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        ModelActivationDocument activation = modelActivationRepository.findByModelName(modelName).orElse(null);
        return ModelOperationsDto.ModelVersionPageResponse.builder()
                .items(page.getContent().stream().map(item -> toVersionItem(item, activation)).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build();
    }

    /**
     * 查询模型版本详情。
     *
     * @param versionId 模型版本 ID
     * @return 模型版本详情
     */
    public ModelOperationsDto.ModelVersionItem getVersionDetails(String versionId) {
        LstmModelDocument document = lstmModelRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("模型版本不存在"));
        ModelActivationDocument activation = modelActivationRepository
                .findByModelName(document.getModelName()).orElse(null);
        return toVersionItem(document, activation);
    }

    /**
     * 分页查询训练运行记录。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 训练运行分页结果
     */
    public ModelOperationsDto.TrainingRunPageResponse listTrainingRuns(int current, int pageSize) {
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        Page<ModelTrainingRunDocument> page = trainingRunRepository.findByModelName(
                lstmTrainerService.getModelName(),
                PageRequest.of(safeCurrent - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ModelOperationsDto.TrainingRunPageResponse.builder()
                .items(page.getContent().stream().map(this::toTrainingRunItem).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build();
    }

    /**
     * 提交受控异步训练任务。
     *
     * @param request 训练请求
     * @return 训练任务提交结果
     */
    public synchronized ModelOperationsDto.OperationResponse startTraining(
            ModelOperationsDto.StartTrainingRequest request) {
        ModelOperationsDto.StartTrainingRequest safeRequest = request == null
                ? new ModelOperationsDto.StartTrainingRequest() : request;
        validateTrainingParameters(safeRequest);
        String modelName = lstmTrainerService.getModelName();
        GateResult gateResult = evaluateTrainingGate(modelName, hasRunningTraining(modelName));
        String operatorName = normalizeOperator(safeRequest.getOperatorName());
        if (!gateResult.allowed()) {
            saveAudit(modelName, "TRAIN_REJECTED", operatorName, null, null, false, gateResult.message());
            throw new IllegalStateException(gateResult.message());
        }

        int days = safeRequest.getDays() == null ? DEFAULT_TRAIN_DAYS : safeRequest.getDays();
        int epochs = safeRequest.getEpochs() == null ? trainingConfig.getEpochs() : safeRequest.getEpochs();
        int batchSize = safeRequest.getBatchSize() == null
                ? trainingConfig.getBatchSize() : safeRequest.getBatchSize();
        double learningRate = safeRequest.getLearningRate() == null
                ? trainingConfig.getLearningRate() : safeRequest.getLearningRate();

        ModelTrainingRunDocument run = new ModelTrainingRunDocument();
        run.setModelName(modelName);
        run.setStatus("QUEUED");
        run.setTriggerSource("WEB_CONSOLE");
        run.setTriggeredBy(operatorName);
        run.setRequestedConfigJson(writeJson(trainingRequestMap(safeRequest)));
        run.setEffectiveConfigJson(writeJson(effectiveConfigMap(days, epochs, batchSize, learningRate)));
        run.setGateResultJson(writeJson(Map.of("allowed", true, "message", gateResult.message())));
        run.setCreatedAt(LocalDateTime.now());
        ModelTrainingRunDocument saved = trainingRunRepository.save(run);
        saveAudit(modelName, "TRAIN_SUBMITTED", operatorName, null, null, true, "训练任务已进入队列");

        applicationTaskExecutor.execute(() -> executeTraining(saved.getId(), days, epochs, batchSize, learningRate));
        return ModelOperationsDto.OperationResponse.builder()
                .operationType("TRAIN")
                .operationId(saved.getId())
                .message("训练任务已提交")
                .build();
    }

    /**
     * 激活已通过门禁的候选模型版本。
     *
     * @param versionId 候选模型版本 ID
     * @param operatorName 操作人名称
     * @return 激活结果
     */
    public synchronized ModelOperationsDto.OperationResponse activateVersion(
            String versionId, String operatorName) {
        ensureModelSwitchAllowed();
        LstmModelDocument target = lstmModelRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("模型版本不存在"));
        if (!"READY".equals(target.getStatus())) {
            throw new IllegalStateException("仅 READY 状态的候选版本允许激活");
        }
        lstmTrainerService.validateModelVersion(versionId);
        ModelActivationDocument activation = modelActivationRepository
                .findByModelName(target.getModelName()).orElseGet(ModelActivationDocument::new);
        String currentVersionId = activation.getActiveModelVersionId();
        activation.setModelName(target.getModelName());
        activation.setPreviousModelVersionId(currentVersionId);
        activation.setActiveModelVersionId(target.getId());
        activation.setActivatedAt(LocalDateTime.now());
        modelActivationRepository.save(activation);

        markVersionStatus(currentVersionId, "ARCHIVED");
        target.setStatus("ACTIVE");
        lstmModelRepository.save(target);
        String operator = normalizeOperator(operatorName);
        saveAudit(target.getModelName(), "ACTIVATE", operator, currentVersionId,
                target.getId(), true, "候选版本已激活");
        return ModelOperationsDto.OperationResponse.builder()
                .operationType("ACTIVATE")
                .operationId(target.getId())
                .message("模型版本已激活")
                .build();
    }

    /**
     * 将指定模型回滚到上一激活版本。
     *
     * @param modelName 模型逻辑名称
     * @param operatorName 操作人名称
     * @return 回滚结果
     */
    public synchronized ModelOperationsDto.OperationResponse rollback(
            String modelName, String operatorName) {
        ensureModelSwitchAllowed();
        ModelActivationDocument activation = modelActivationRepository.findByModelName(modelName)
                .orElseThrow(() -> new IllegalStateException("模型尚未建立激活指针"));
        String targetVersionId = activation.getPreviousModelVersionId();
        if (targetVersionId == null || targetVersionId.isBlank()) {
            throw new IllegalStateException("当前模型没有可回滚的上一版本");
        }
        lstmTrainerService.validateModelVersion(targetVersionId);
        String currentVersionId = activation.getActiveModelVersionId();
        activation.setActiveModelVersionId(targetVersionId);
        activation.setPreviousModelVersionId(currentVersionId);
        activation.setActivatedAt(LocalDateTime.now());
        modelActivationRepository.save(activation);

        markVersionStatus(currentVersionId, "ARCHIVED");
        markVersionStatus(targetVersionId, "ACTIVE");
        String operator = normalizeOperator(operatorName);
        saveAudit(modelName, "ROLLBACK", operator, currentVersionId,
                targetVersionId, true, "模型已回滚到上一版本");
        return ModelOperationsDto.OperationResponse.builder()
                .operationType("ROLLBACK")
                .operationId(targetVersionId)
                .message("模型已回滚到上一版本")
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
        trainingRunRepository.save(run);
        try {
            List<String> stockCodes = stockInfoRepository.findAllCodes().stream()
                    .filter(code -> dataQualityConfig.getSkipTrainingCodes() == null
                            || !dataQualityConfig.getSkipTrainingCodes().contains(code))
                    .toList();
            LstmTrainerService.TrainingResult result = lstmTrainerService.trainSharedModel(
                    stockCodes, days, epochs, batchSize, learningRate);
            if (result == null || !result.isSuccess()) {
                throw new IllegalStateException(result == null ? "训练无返回结果" : result.getMessage());
            }
            String candidateVersionId = parseModelVersionId(result.getModelPath());
            lstmTrainerService.validateModelVersion(candidateVersionId);
            LstmModelDocument candidate = lstmModelRepository.findById(candidateVersionId)
                    .orElseThrow(() -> new IllegalStateException("候选模型版本不存在"));
            candidate.setStatus("READY");
            lstmModelRepository.save(candidate);

            run.setStatus("SUCCEEDED");
            run.setCandidateModelVersionId(candidateVersionId);
            run.setTrainSamples(result.getTrainSamples());
            run.setValSamples(result.getValSamples());
            run.setTrainLoss(result.getTrainLoss());
            run.setValLoss(result.getValLoss());
            saveAudit(run.getModelName(), "TRAIN_COMPLETED", run.getTriggeredBy(),
                    null, candidateVersionId, true, "候选模型训练和兼容性校验通过");
        } catch (Exception exception) {
            log.error("受控模型训练失败: runId={}", runId, exception);
            run.setStatus("FAILED");
            run.setErrorMessage(limitErrorMessage(exception.getMessage()));
            saveAudit(run.getModelName(), "TRAIN_FAILED", run.getTriggeredBy(),
                    null, null, false, run.getErrorMessage());
        } finally {
            run.setFinishedAt(LocalDateTime.now());
            trainingRunRepository.save(run);
        }
    }

    private GateResult evaluateTrainingGate(String modelName, boolean trainingRunning) {
        if (!trainingConfig.isTrainingEnabled()) {
            return new GateResult(false, "当前节点未开启 LSTM 训练能力");
        }
        if (tradingTimeChecker.isTradingTime()) {
            return new GateResult(false, "交易时段禁止执行模型训练");
        }
        if (!isTrainingWindowOpen(LocalDateTime.now())) {
            return new GateResult(false, "工作日仅允许在 18:30 至 23:30 提交训练");
        }
        if (trainingRunning) {
            return new GateResult(false, "当前模型已有排队或运行中的训练任务");
        }
        if (stockInfoRepository.countBy() <= 0) {
            return new GateResult(false, "股票基础数据为空，无法训练");
        }
        if (availableJvmMemory() < MIN_AVAILABLE_MEMORY_BYTES) {
            return new GateResult(false, "JVM 可用内存低于 256MB，拒绝启动训练");
        }
        return new GateResult(true, "训练门禁已通过");
    }

    private boolean isTrainingWindowOpen(LocalDateTime now) {
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return true;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(WEEKDAY_TRAINING_START) && !time.isAfter(WEEKDAY_TRAINING_END);
    }

    private boolean hasRunningTraining(String modelName) {
        return trainingRunRepository.existsByModelNameAndStatusIn(modelName, RUNNING_STATUSES);
    }

    private void ensureModelSwitchAllowed() {
        if (tradingTimeChecker.isTradingTime()) {
            throw new IllegalStateException("交易时段禁止切换模型版本");
        }
        if (hasRunningTraining(lstmTrainerService.getModelName())) {
            throw new IllegalStateException("训练任务运行期间禁止切换模型版本");
        }
    }

    private void validateTrainingParameters(ModelOperationsDto.StartTrainingRequest request) {
        validateRange("训练天数", request.getDays(), 90, 2000);
        validateRange("训练轮次", request.getEpochs(), 1, 500);
        validateRange("批次大小", request.getBatchSize(), 8, 512);
        if (request.getLearningRate() != null
                && (request.getLearningRate() < 0.000001D || request.getLearningRate() > 0.1D)) {
            throw new IllegalArgumentException("学习率必须在 0.000001 到 0.1 之间");
        }
    }

    private void validateRange(String name, Integer value, int min, int max) {
        if (value != null && (value < min || value > max)) {
            throw new IllegalArgumentException(name + "必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private ModelOperationsDto.ModelVersionItem versionById(
            String versionId, ModelActivationDocument activation) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        return lstmModelRepository.findById(versionId)
                .map(document -> toVersionItem(document, activation))
                .orElse(null);
    }

    private ModelOperationsDto.ModelVersionItem toVersionItem(
            LstmModelDocument document, ModelActivationDocument activation) {
        String activeId = activation == null ? null : activation.getActiveModelVersionId();
        String previousId = activation == null ? null : activation.getPreviousModelVersionId();
        return ModelOperationsDto.ModelVersionItem.builder()
                .id(document.getId())
                .modelName(document.getModelName())
                .modelVersion(document.getModelVersion())
                .parentModelVersionId(document.getParentModelVersionId())
                .status(document.getStatus())
                .featureVersion(document.getFeatureVersion())
                .labelVersion(document.getLabelVersion())
                .engineName(document.getEngineName())
                .engineVersion(document.getEngineVersion())
                .djlVersion(document.getDjlVersion())
                .parameterSha256(document.getParameterSha256())
                .parameterSize(document.getParameterSize())
                .epoch(document.getEpoch())
                .trainLoss(document.getTrainLoss())
                .valLoss(document.getValLoss())
                .trainingConfigJson(document.getTrainingConfigJson())
                .inputContractJson(document.getInputContractJson())
                .metricsJson(document.getMetricsJson())
                .createdAt(document.getCreatedAt())
                .active(document.getId() != null && document.getId().equals(activeId))
                .previous(document.getId() != null && document.getId().equals(previousId))
                .build();
    }

    private ModelOperationsDto.TrainingRunItem toTrainingRunItem(ModelTrainingRunDocument run) {
        if (run == null) {
            return null;
        }
        return ModelOperationsDto.TrainingRunItem.builder()
                .id(run.getId())
                .modelName(run.getModelName())
                .status(run.getStatus())
                .triggerSource(run.getTriggerSource())
                .triggeredBy(run.getTriggeredBy())
                .requestedConfigJson(run.getRequestedConfigJson())
                .effectiveConfigJson(run.getEffectiveConfigJson())
                .gateResultJson(run.getGateResultJson())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .candidateModelVersionId(run.getCandidateModelVersionId())
                .errorMessage(run.getErrorMessage())
                .trainSamples(run.getTrainSamples())
                .valSamples(run.getValSamples())
                .trainLoss(run.getTrainLoss())
                .valLoss(run.getValLoss())
                .createdAt(run.getCreatedAt())
                .build();
    }

    private boolean isActiveVersionHealthy(ModelActivationDocument activation) {
        if (activation == null || activation.getActiveModelVersionId() == null) {
            return false;
        }
        try {
            lstmTrainerService.validateModelVersion(activation.getActiveModelVersionId());
            return true;
        } catch (RuntimeException exception) {
            log.warn("当前激活模型健康检查失败: {}", exception.getMessage());
            return false;
        }
    }

    private void markVersionStatus(String versionId, String status) {
        if (versionId == null || versionId.isBlank()) {
            return;
        }
        Optional<LstmModelDocument> optional = lstmModelRepository.findById(versionId);
        if (optional.isPresent()) {
            LstmModelDocument document = optional.get();
            document.setStatus(status);
            lstmModelRepository.save(document);
        }
    }

    private void saveAudit(String modelName, String operationType, String operatorName,
                           String sourceVersionId, String targetVersionId,
                           boolean success, String reason) {
        ModelOperationAuditDocument audit = new ModelOperationAuditDocument();
        audit.setModelName(modelName);
        audit.setOperationType(operationType);
        audit.setOperatorName(normalizeOperator(operatorName));
        audit.setSourceVersionId(sourceVersionId);
        audit.setTargetVersionId(targetVersionId);
        audit.setSuccess(success);
        audit.setReason(limitErrorMessage(reason));
        audit.setCreatedAt(LocalDateTime.now());
        auditRepository.save(audit);
    }

    private Map<String, Object> trainingRequestMap(ModelOperationsDto.StartTrainingRequest request) {
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

    private record GateResult(boolean allowed, String message) {
    }
}
// AI_GENERATE_END --
