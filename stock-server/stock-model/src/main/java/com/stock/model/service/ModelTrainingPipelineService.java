// AI_GENERATE_START- ------
package com.stock.model.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.data.service.MarketDataQueryService;
import com.stock.model.algorithm.LstmArchitectureType;
import com.stock.model.config.LstmDataQualityConfig;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.dto.ModelDataBackfillResult;
import com.stock.model.dto.ModelDataReadinessReport;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.entity.ModelTrainingRunDocument;
import com.stock.model.entity.TrainingDatasetSnapshotDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import com.stock.model.repository.ModelTrainingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型训练统一流水线服务。
 * 手动和定时触发均依次执行数据盘点、自动数据准备、快照、训练、离线评估和候选登记。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelTrainingPipelineService {

    private final LstmTrainingConfig trainingConfig;
    private final LstmDataQualityConfig dataQualityConfig;
    private final MarketDataQueryService marketDataQueryService;
    private final ModelDataReadinessService readinessService;
    private final ModelDataBackfillService backfillService;
    private final TrainingDatasetSnapshotService snapshotService;
    private final LstmTrainerService lstmTrainerService;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository activationRepository;
    private final ModelTrainingRunRepository trainingRunRepository;
    private final ObjectMapper objectMapper;
    private final ModelOperationAuditService auditService;
    private final ModelPromotionPolicy promotionPolicy;
    private final ModelActivationService activationService;
    private final TemporalModelEnsembleService temporalModelEnsembleService;

    /**
     * 执行一次完整模型训练流水线。
     *
     * @param runId 训练运行 ID
     * @param days 训练交易日数量
     * @param epochs 训练轮次
     * @param batchSize 批次大小
     * @param learningRate 学习率
     * @param architectureType 候选模型架构
     * @return 流水线执行结果
     */
    public PipelineExecutionResult execute(
            String runId,
            int days,
            int epochs,
            int batchSize,
            double learningRate,
            LstmArchitectureType architectureType) {
        ModelTrainingRunDocument run = trainingRunRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("模型训练运行记录不存在: runId={}", runId);
            return new PipelineExecutionResult(false, false, null);
        }
        startRun(run, epochs);
        try {
            requireAvailableMemory();
            List<String> stockCodes = loadTrainingStockCodes();
            checkTrainingStopRequested(run.getId());
            int snapshotDays = Math.max(days,
                    trainingConfig.getSequenceLength() + trainingConfig.getValidationGap() + 2);
            ModelDataReadinessReport beforeBackfill = inspectData(run, stockCodes, "CHECKING_DATA", 3);
            List<ModelDataBackfillResult> backfillResults = new ArrayList<>();
            ModelDataReadinessReport afterBackfill = beforeBackfill;
            int maximumPreparationRounds = Math.max(1, trainingConfig.getDataPreparationMaxRounds());
            for (int round = 1; !afterBackfill.isReady()
                    && afterBackfill.isAutomaticBackfillAvailable()
                    && round <= maximumPreparationRounds; round++) {
                ModelDataReadinessReport roundBefore = afterBackfill;
                updateStage(run, "PREPARING_DATA",
                        "正在自动准备 V6 训练数据，第 " + round + " 轮", Math.min(30, 5 + round));
                ModelDataBackfillResult backfillResult = backfillService.backfillOnce(
                        roundBefore, stockCodes, snapshotDays);
                backfillResults.add(backfillResult);
                checkTrainingStopRequested(run.getId());
                afterBackfill = inspectData(run, stockCodes, "RECHECKING_DATA", Math.min(32, 8 + round));
                if (!hasDataPreparationProgress(roundBefore, afterBackfill)) {
                    break;
                }
            }
            recordDataGate(run, beforeBackfill, backfillResults, afterBackfill);
            if (!afterBackfill.isReady()) {
                return markWaitingData(run, afterBackfill);
            }

            TrainingDatasetSnapshotService.SnapshotPreparationResult preparation =
                    snapshotService.prepareSnapshot(
                            run.getModelName(), stockCodes, snapshotDays,
                            snapshot -> recordPreparingSnapshot(run, snapshot));
            TrainingDatasetSnapshotDocument snapshot = preparation.snapshot();
            recordReadySnapshot(run, snapshot, preparation.reused());
            checkTrainingStopRequested(run.getId());
            LstmTrainerService.TrainingResult result = lstmTrainerService.trainSnapshot(
                    snapshot.getId(), epochs, batchSize, learningRate,
                    progress -> updateTrainingProgress(run, progress), architectureType);
            applyTrainingResult(run, result);
            if (result == null || !result.isSuccess()) {
                throw new IllegalStateException(result == null ? "训练无返回结果" : result.getMessage());
            }

            String candidateVersionId = requireCandidateVersionId(result.getModelPath());
            updateStage(run, "OFFLINE_EVALUATING", "正在执行候选模型一次性离线评估", 98);
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
            updateStage(run, "CANDIDATE_REGISTERED", "候选模型已完成登记", 99);
            completeRun(run, candidateVersionId);
            boolean activeCandidate = isActiveCandidate(candidateVersionId);
            return new PipelineExecutionResult(true, activeCandidate, candidateVersionId);
        } catch (ModelTrainingDataNotReadyException exception) {
            recordSnapshotDataGate(run, exception.getMessage());
            return markWaitingData(run, exception.getMessage());
        } catch (LstmTrainerService.TrainingCancelledException exception) {
            cancelRun(run, exception.getMessage());
            return new PipelineExecutionResult(false, false, run.getCandidateModelVersionId());
        } catch (Exception exception) {
            failRun(run, exception);
            return new PipelineExecutionResult(false, false, run.getCandidateModelVersionId());
        } finally {
            if (run.getFinishedAt() == null) {
                run.setFinishedAt(LocalDateTime.now());
            }
            trainingRunRepository.save(run);
        }
    }

    private void startRun(ModelTrainingRunDocument run, int epochs) {
        run.setStatus("RUNNING");
        run.setStartedAt(LocalDateTime.now());
        run.setProgressStage("CHECKING_DATA");
        run.setProgressMessage("正在检查训练基础数据质量");
        run.setProgressPercent(1);
        run.setCurrentEpoch(0);
        run.setTotalEpochs(epochs);
        run.setElapsedSeconds(0L);
        run.setLastProgressAt(LocalDateTime.now());
        trainingRunRepository.save(run);
    }

    private List<String> loadTrainingStockCodes() {
        List<String> stockCodes = marketDataQueryService.findTradableStockCodes().stream()
                .filter(code -> dataQualityConfig.getSkipTrainingCodes() == null
                        || !dataQualityConfig.getSkipTrainingCodes().contains(code))
                .toList();
        if (stockCodes.isEmpty()) {
            throw new IllegalStateException("股票基础数据为空，无法训练");
        }
        return stockCodes;
    }

    private ModelDataReadinessReport inspectData(
            ModelTrainingRunDocument run,
            List<String> stockCodes,
            String stage,
            int progressPercent) {
        updateStage(run, stage, "正在审计模型基础数据和新闻快照", progressPercent);
        ModelDataReadinessReport report = readinessService.inspect(stockCodes);
        run.setProgressMessage(report.getMessage());
        run.setLastProgressAt(LocalDateTime.now());
        trainingRunRepository.save(run);
        return report;
    }

    private void recordDataGate(
            ModelTrainingRunDocument run,
            ModelDataReadinessReport beforeBackfill,
            List<ModelDataBackfillResult> backfillResults,
            ModelDataReadinessReport afterBackfill) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("allowed", afterBackfill.isReady());
        gate.put("phase", "DATA_READINESS");
        gate.put("beforeBackfill", beforeBackfill);
        gate.put("backfillRounds", backfillResults);
        gate.put("afterBackfill", afterBackfill);
        run.setGateResultJson(writeJson(gate));
        trainingRunRepository.save(run);
    }

    private boolean hasDataPreparationProgress(
            ModelDataReadinessReport before, ModelDataReadinessReport after) {
        if (before == null || after == null) {
            return false;
        }
        if (after.isReady() && !before.isReady()) {
            return true;
        }
        if (before.getFoundationAudit() != null && after.getFoundationAudit() != null) {
            if (after.getFoundationAudit().getModelBarStockCount()
                    > before.getFoundationAudit().getModelBarStockCount()) {
                return true;
            }
            if (after.getFoundationAudit().getLatestModelBarDate() != null
                    && (before.getFoundationAudit().getLatestModelBarDate() == null
                    || after.getFoundationAudit().getLatestModelBarDate()
                    .isAfter(before.getFoundationAudit().getLatestModelBarDate()))) {
                return true;
            }
        }
        return before.getNewsSnapshotAudit() != null && after.getNewsSnapshotAudit() != null
                && (after.getNewsSnapshotAudit().snapshotCount()
                > before.getNewsSnapshotAudit().snapshotCount()
                || after.getNewsSnapshotAudit().coverageRate()
                > before.getNewsSnapshotAudit().coverageRate());
    }

    private PipelineExecutionResult markWaitingData(
            ModelTrainingRunDocument run, ModelDataReadinessReport report) {
        return markWaitingData(run, report.getMessage());
    }

    private PipelineExecutionResult markWaitingData(
            ModelTrainingRunDocument run, String message) {
        run.setStatus("WAITING_DATA");
        run.setProgressStage("WAITING_DATA");
        run.setProgressMessage(message);
        run.setProgressPercent(Math.max(0, run.getProgressPercent() == null ? 0 : run.getProgressPercent()));
        run.setEstimatedRemainingSeconds(0L);
        run.setFinishedAt(LocalDateTime.now());
        run.setLastProgressAt(LocalDateTime.now());
        auditService.record(run.getModelName(), "TRAIN_WAITING_DATA", run.getTriggeredBy(),
                null, null, true, message);
        return new PipelineExecutionResult(false, false, null);
    }

    private void recordSnapshotDataGate(ModelTrainingRunDocument run, String message) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("allowed", false);
        gate.put("phase", "SNAPSHOT_DATA_PREPARATION");
        gate.put("message", message);
        run.setGateResultJson(writeJson(gate));
        trainingRunRepository.save(run);
    }

    private void applyTrainingResult(
            ModelTrainingRunDocument run, LstmTrainerService.TrainingResult result) {
        if (result == null) {
            return;
        }
        run.setTrainedStocks(result.getTrainedStocks());
        run.setTrainSamples(result.getTrainSamples());
        run.setValSamples(result.getValSamples());
        run.setTrainLoss(result.getTrainLoss());
        run.setValLoss(result.getValLoss());
        run.setDatasetSnapshotId(result.getSnapshotId());
    }

    private void updateTrainingProgress(
            ModelTrainingRunDocument run, LstmTrainerService.TrainingProgress progress) {
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

    private void recordPreparingSnapshot(
            ModelTrainingRunDocument run, TrainingDatasetSnapshotDocument snapshot) {
        ModelTrainingRunDocument current = trainingRunRepository.findById(run.getId()).orElse(run);
        current.setDatasetSnapshotId(snapshot.getId());
        current.setProgressStage("PREPARING_SNAPSHOT");
        current.setProgressMessage("训练数据快照状态：" + snapshot.getStatus());
        current.setProgressPercent(15);
        current.setLastProgressAt(LocalDateTime.now());
        trainingRunRepository.save(current);
        run.setDatasetSnapshotId(snapshot.getId());
    }

    private void recordReadySnapshot(
            ModelTrainingRunDocument run,
            TrainingDatasetSnapshotDocument snapshot,
            boolean reused) {
        ModelTrainingRunDocument current = trainingRunRepository.findById(run.getId()).orElse(run);
        current.setDatasetSnapshotId(snapshot.getId());
        current.setTrainedStocks(snapshot.getTrainedStocks());
        current.setTrainSamples(snapshot.getTrainSamples());
        current.setValSamples(snapshot.getValSamples());
        current.setProgressStage("SNAPSHOT_READY");
        current.setProgressMessage(reused ? "已复用完成的训练数据快照" : "训练数据快照提取完成");
        current.setProgressPercent(35);
        current.setLastProgressAt(LocalDateTime.now());
        trainingRunRepository.save(current);
        run.setDatasetSnapshotId(snapshot.getId());
        run.setTrainedStocks(snapshot.getTrainedStocks());
        run.setTrainSamples(snapshot.getTrainSamples());
        run.setValSamples(snapshot.getValSamples());
        auditService.record(run.getModelName(), reused ? "SNAPSHOT_REUSED" : "SNAPSHOT_BUILT",
                run.getTriggeredBy(), null, snapshot.getId(), true, current.getProgressMessage());
    }

    private void updateStage(
            ModelTrainingRunDocument run, String stage, String message, int progressPercent) {
        run.setProgressStage(stage);
        run.setProgressMessage(message);
        run.setProgressPercent(progressPercent);
        run.setLastProgressAt(LocalDateTime.now());
        trainingRunRepository.save(run);
    }

    private void completeRun(ModelTrainingRunDocument run, String candidateVersionId) {
        run.setStatus("SUCCEEDED");
        run.setProgressStage("COMPLETED");
        run.setProgressMessage("训练完成，候选模型已完成一次性离线评估和登记");
        run.setProgressPercent(100);
        run.setEstimatedRemainingSeconds(0L);
        run.setFinishedAt(LocalDateTime.now());
        run.setLastProgressAt(LocalDateTime.now());
        auditService.record(run.getModelName(), "TRAIN_COMPLETED", run.getTriggeredBy(),
                null, candidateVersionId, true, "候选模型训练、离线评估和登记完成");
    }

    private void cancelRun(ModelTrainingRunDocument run, String message) {
        log.info("受控模型训练已停止: runId={}, reason={}", run.getId(), message);
        run.setStatus("CANCELLED");
        run.setProgressStage("CANCELLED");
        run.setProgressMessage(message);
        run.setEstimatedRemainingSeconds(0L);
        run.setLastProgressAt(LocalDateTime.now());
        auditService.record(run.getModelName(), "TRAIN_CANCELLED", run.getTriggeredBy(),
                null, null, true, message);
    }

    private void failRun(ModelTrainingRunDocument run, Exception exception) {
        log.error("受控模型训练失败: runId={}", run.getId(), exception);
        run.setStatus("FAILED");
        run.setErrorMessage(limitErrorMessage(exception.getMessage()));
        run.setProgressStage("FAILED");
        run.setProgressMessage("训练失败：" + run.getErrorMessage());
        run.setEstimatedRemainingSeconds(0L);
        run.setLastProgressAt(LocalDateTime.now());
        auditService.record(run.getModelName(), "TRAIN_FAILED", run.getTriggeredBy(),
                null, null, false, run.getErrorMessage());
    }

    private void checkTrainingStopRequested(String runId) {
        ModelTrainingRunDocument current = trainingRunRepository.findById(runId).orElse(null);
        if (current != null && "STOPPING".equals(current.getStatus())) {
            throw new LstmTrainerService.TrainingCancelledException("训练已按请求停止");
        }
    }

    private void requireAvailableMemory() {
        long minimumMemoryBytes = Math.max(1L, dataQualityConfig.getMinimumAvailableMemoryMb())
                * 1024L * 1024L;
        Runtime runtime = Runtime.getRuntime();
        long available = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory());
        if (available < minimumMemoryBytes) {
            throw new IllegalStateException("JVM 可用内存低于 "
                    + dataQualityConfig.getMinimumAvailableMemoryMb() + "MB，拒绝启动训练");
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
            LstmModelDocument candidate,
            LstmArchitectureType architectureType,
            double candidateScore,
            String operatorName) {
        candidate.setStatus("CANDIDATE");
        lstmModelRepository.save(candidate);
        if (architectureType == LstmArchitectureType.MASTER_TRANSFORMER) {
            temporalModelEnsembleService.registerMasterCandidate(candidate.getId());
            auditService.record(candidate.getModelName(), "MASTER_CANDIDATE_REGISTERED", operatorName,
                    null, candidate.getId(), true, "MASTER 候选已自动进入长期影子评估");
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

    private boolean isActiveCandidate(String candidateVersionId) {
        return candidateVersionId != null
                && lstmModelRepository.findById(candidateVersionId)
                .map(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElse(false);
    }

    private String requireCandidateVersionId(String modelPath) {
        if (modelPath == null || modelPath.isBlank()) {
            throw new IllegalStateException("训练结果缺少候选模型版本 ID");
        }
        return modelPath;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("模型训练数据门禁结果序列化失败", exception);
        }
    }

    private String limitErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        return message.substring(0, Math.min(1000, message.length()));
    }

    /**
     * 模型训练流水线执行结果。
     *
     * @param succeeded 流水线是否完成训练和候选登记
     * @param activeCandidate 基础候选是否已自动晋级为激活版本
     * @param candidateVersionId 候选模型版本 ID
     */
    public record PipelineExecutionResult(
            boolean succeeded,
            boolean activeCandidate,
            String candidateVersionId) {
    }
}
// AI_GENERATE_END- ------
