// AI_GENERATE_START---
package com.stock.model.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.model.dto.ModelOverviewResponse;
import com.stock.model.dto.ModelScoreSummaryResponse;
import com.stock.model.dto.ModelTrainingRunPageResponse;
import com.stock.model.dto.ModelTrainingRunResponse;
import com.stock.model.dto.ModelVersionPageResponse;
import com.stock.model.dto.ModelVersionResponse;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelTrainingRunDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelTrainingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型运维只读查询服务。
 *
 * 负责模型概览、版本、训练运行记录查询以及响应对象转换。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelOperationsQueryService {

    private static final List<String> RUNNING_STATUSES = List.of("QUEUED", "RUNNING", "STOPPING");

    private final LstmTrainerService trainerService;
    private final LstmModelRepository modelRepository;
    private final ModelActivationRepository activationRepository;
    private final ModelTrainingRunRepository trainingRunRepository;
    private final ModelPredictionBatchRepository predictionBatchRepository;
    private final ObjectMapper objectMapper;

    /**
     * 查询模型运维概览。
     *
     * @return 模型运维概览
     */
    public ModelOverviewResponse getOverview() {
        String modelName = trainerService.getModelName();
        ModelActivationDocument activation = activationRepository.findByModelName(modelName).orElse(null);
        ModelVersionResponse activeVersion = versionById(
                activation == null ? null : activation.getActiveModelVersionId(), activation);
        ModelVersionResponse previousVersion = versionById(
                activation == null ? null : activation.getPreviousModelVersionId(), activation);
        return ModelOverviewResponse.builder()
                .modelName(modelName)
                .activeVersion(activeVersion)
                .previousVersion(previousVersion)
                .latestTrainingRun(toTrainingRunItem(
                        trainingRunRepository.findTopByModelNameOrderByCreatedAtDesc(modelName)))
                .trainingRunning(trainingRunRepository.existsByModelNameAndStatusIn(modelName, RUNNING_STATUSES))
                .activeModelHealthy(isActiveVersionHealthy(activation))
                .activeVersionScore(loadActiveVersionScore(activeVersion))
                .build();
    }

    /**
     * 分页查询模型版本。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 模型版本分页结果
     */
    public ModelVersionPageResponse listVersions(int current, int pageSize) {
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        String modelName = trainerService.getModelName();
        Page<LstmModelDocument> page = modelRepository.findByModelName(modelName,
                PageRequest.of(safeCurrent - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        ModelActivationDocument activation = activationRepository.findByModelName(modelName).orElse(null);
        return ModelVersionPageResponse.builder()
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
    public ModelVersionResponse getVersionDetails(String versionId) {
        LstmModelDocument document = modelRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("模型版本不存在"));
        ModelActivationDocument activation = activationRepository
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
    public ModelTrainingRunPageResponse listTrainingRuns(int current, int pageSize) {
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        Page<ModelTrainingRunDocument> page = trainingRunRepository.findByModelName(
                trainerService.getModelName(),
                PageRequest.of(safeCurrent - 1, safePageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ModelTrainingRunPageResponse.builder()
                .items(page.getContent().stream().map(this::toTrainingRunItem).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build();
    }

    private ModelVersionResponse versionById(String versionId, ModelActivationDocument activation) {
        if (versionId == null || versionId.isBlank()) {
            return null;
        }
        return modelRepository.findById(versionId)
                .map(document -> toVersionItem(document, activation))
                .orElse(null);
    }

    private ModelScoreSummaryResponse loadActiveVersionScore(ModelVersionResponse activeVersion) {
        if (activeVersion == null || activeVersion.getId() == null || activeVersion.getId().isBlank()) {
            return null;
        }
        ModelPredictionBatchDocument batch = predictionBatchRepository
                .findFirstByModelIdAndEvaluatedCountGreaterThanOrderByEvaluationFinishedAtDescCreatedAtDesc(
                        activeVersion.getId(), 0);
        if (batch == null) {
            return null;
        }
        return ModelScoreSummaryResponse.builder()
                .batchId(batch.getId())
                .modelId(batch.getModelId())
                .modelVersion(batch.getModelVersion())
                .targetTradeDate(batch.getTargetTradeDate())
                .evaluatedCount(batch.getEvaluatedCount())
                .coverageRate(batch.getCoverageRate())
                .directionAccuracy(batch.getDirectionAccuracy())
                .topDecileDirectionAccuracy(batch.getTopDecileDirectionAccuracy())
                .highConfidenceAccuracy(batch.getHighConfidenceAccuracy())
                .spearmanRankIc(batch.getSpearmanRankIc())
                .abilityLevel(batch.getAbilityLevel())
                .build();
    }

    private ModelVersionResponse toVersionItem(LstmModelDocument document, ModelActivationDocument activation) {
        String activeId = activation == null ? null : activation.getActiveModelVersionId();
        String previousId = activation == null ? null : activation.getPreviousModelVersionId();
        JsonNode inputContract = readJson(document.getInputContractJson());
        JsonNode metrics = readJson(document.getMetricsJson());
        return ModelVersionResponse.builder()
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
                .architectureType(textValue(inputContract, "architectureType"))
                .outputSchemaVersion(textValue(inputContract, "outputSchemaVersion"))
                .walkForwardMeanRankIc(doubleValue(metrics, "walkForwardMeanRankIc"))
                .walkForwardRankIcIr(doubleValue(metrics, "walkForwardRankIcIr"))
                .positiveRankIcWindowRatio(doubleValue(metrics, "positiveRankIcWindowRatio"))
                .walkForwardTopDecileNetReturn(doubleValue(metrics, "walkForwardTopDecileNetReturn"))
                .brierScore(doubleValue(metrics, "walkForwardBrierScore"))
                .brierBaseline(doubleValue(metrics, "walkForwardBrierBaseline"))
                .outputCollapsed(booleanValue(metrics, "outputCollapsed"))
                .createdAt(document.getCreatedAt())
                .active(document.getId() != null && document.getId().equals(activeId))
                .previous(document.getId() != null && document.getId().equals(previousId))
                .build();
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            log.warn("模型版本 JSON 元数据解析失败: {}", exception.getMessage());
            return null;
        }
    }

    private String textValue(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) ? null : node.path(field).asText();
    }

    private Double doubleValue(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) || !node.path(field).isNumber()
                ? null : node.path(field).asDouble();
    }

    private Boolean booleanValue(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) || !node.path(field).isBoolean()
                ? null : node.path(field).asBoolean();
    }

    private ModelTrainingRunResponse toTrainingRunItem(ModelTrainingRunDocument run) {
        if (run == null) {
            return null;
        }
        return ModelTrainingRunResponse.builder()
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
                .datasetSnapshotId(run.getDatasetSnapshotId())
                .errorMessage(run.getErrorMessage())
                .trainedStocks(run.getTrainedStocks())
                .progressStage(run.getProgressStage())
                .progressMessage(run.getProgressMessage())
                .progressPercent(run.getProgressPercent())
                .currentEpoch(run.getCurrentEpoch())
                .totalEpochs(run.getTotalEpochs())
                .elapsedSeconds(run.getElapsedSeconds())
                .estimatedRemainingSeconds(run.getEstimatedRemainingSeconds())
                .lastProgressAt(run.getLastProgressAt())
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
            trainerService.validateModelVersion(activation.getActiveModelVersionId());
            return true;
        } catch (RuntimeException exception) {
            log.warn("当前激活模型健康检查失败: {}", exception.getMessage());
            return false;
        }
    }
}
// AI_GENERATE_END---
