// AI_GENERATE_START -----
package com.stock.modelService.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.dto.SentimentAnalyzeRequestDto;
import com.stock.modelService.domain.dto.SentimentAnalyzeResultDto;
import com.stock.modelService.domain.dto.SentimentHealthDto;
import com.stock.modelService.domain.dto.SentimentEvaluationDto;
import com.stock.modelService.domain.vo.SentimentArtifactInspection;
import com.stock.modelService.domain.vo.SentimentArtifactManifest;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import com.stock.modelService.service.SentimentInferenceService;
import com.stock.modelService.service.SentimentEvaluationService;
import com.stock.modelService.service.SentimentAggregationService;
import com.stock.modelService.domain.vo.SentimentAggregateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 情感模型推理接口。
 * 生产服务只开放推理和健康检查，不允许在线训练、下载或切换模型。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Slf4j
@RestController
@RequestMapping("/api/modelSentiment")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentInferenceService inferenceService;
    private final SentimentEvaluationService evaluationService;
    private final SentimentAggregationService aggregationService;

    /**
     * 分析单条新闻或公告文本。
     *
     * @param request 请求文本
     * @return 情感分析结果
     */
    @PostMapping("/analyze")
    public ResponseDTO<SentimentAnalyzeResultDto> analyze(@RequestBody SentimentAnalyzeRequestDto request) {
        String text = request == null ? null : request.getText();
        log.info("执行情感模型推理: textLength={}", text == null ? 0 : text.length());
        if (text == null || text.isBlank()) {
            return ResponseDTO.error("文本不能为空");
        }
        try {
            SentimentAnalysisResult modelResult = inferenceService.analyzeSentimentRequired(text);
            SentimentAnalyzeResultDto result = toDto(modelResult);
            SentimentAggregateResult businessAssessment = aggregationService.assessText(text, "新闻", modelResult);
            return ResponseDTO.success(enrichBusinessAssessment(result, businessAssessment));
        } catch (RuntimeException exception) {
            log.error("情感模型推理失败", exception);
            return ResponseDTO.error("情感模型推理失败：" + exception.getMessage());
        }
    }

    /**
     * 查询情感模型、制品校验和离线评估摘要。
     *
     * @return 模型健康状态
     */
    @GetMapping("/health")
    public ResponseDTO<SentimentHealthDto> health() {
        log.info("查询情感模型健康状态");
        boolean modelLoaded = inferenceService.isModelLoaded();
        SentimentArtifactInspection inspection = inferenceService.getArtifactInspection();
        SentimentArtifactManifest.EvaluationMetrics metrics = inspection.getMetrics();
        String loadError = inferenceService.getLastLoadError();
        String error = loadError != null ? loadError : inspection.getErrorMessage();
        return ResponseDTO.success(SentimentHealthDto.builder()
                .status(modelLoaded ? "UP" : inspection.isAvailable() ? "ARTIFACT_READY" : "NOT_READY")
                .service("Sentiment Analysis Service")
                .modelLoaded(modelLoaded)
                .lastLoadedTime(inferenceService.getLastLoadedTime())
                .artifactAvailable(inspection.isAvailable())
                .manifestPresent(inspection.isManifestPresent())
                .checksumsVerified(inspection.isChecksumsVerified())
                .modelVersion(inspection.getModelVersion())
                .artifactPath(inspection.getModelDirectory() == null
                        ? null : inspection.getModelDirectory().toString())
                .totalSizeBytes(inspection.getTotalSizeBytes())
                .manifestSha256(inspection.getManifestSha256())
                .evaluationDatasetVersion(metrics == null ? null : metrics.getDatasetVersion())
                .evaluationSampleCount(metrics == null ? null : metrics.getSampleCount())
                .macroF1(metrics == null ? null : metrics.getMacroF1())
                .negativeRecall(metrics == null ? null : metrics.getNegativeRecall())
                .expectedCalibrationError(metrics == null ? null : metrics.getExpectedCalibrationError())
                .p95LatencyMs(metrics == null ? null : metrics.getP95LatencyMs())
                .evaluationConclusion(metrics == null ? null : metrics.getConclusion())
                .lastError(error)
                .build());
    }

    /**
     * 查询最近一次人工标注集独立评估结果。
     *
     * @return 独立评估指标
     */
    @GetMapping("/evaluation/latest")
    public ResponseDTO<SentimentEvaluationDto> latestEvaluation() {
        log.info("查询情感模型最近独立评估结果");
        return ResponseDTO.success(evaluationService.getLatest());
    }

    private SentimentAnalyzeResultDto toDto(SentimentAnalysisResult result) {
        return SentimentAnalyzeResultDto.builder()
                .success(true)
                .label(result.getLabel())
                .score(result.getScore())
                .normalizedScore(result.getNormalizedScore())
                .confidence(result.getConfidence())
                .probabilities(result.getProbabilities() == null ? java.util.Map.of() : result.getProbabilities())
                .text(result.getText() == null ? "" : result.getText())
                .modelLoaded(true)
                .build();
    }

    private SentimentAnalyzeResultDto enrichBusinessAssessment(SentimentAnalyzeResultDto result,
                                                               SentimentAggregateResult assessment) {
        result.setEventType(assessment.getDominantEventType());
        result.setImpactDirection(assessment.getImpactDirection());
        result.setImpactMagnitude(assessment.getImpactMagnitude());
        result.setNewsImpactScore(assessment.getNewsImpactScore());
        result.setEffectiveHorizonHours(assessment.getEffectiveHorizonHours());
        result.setActionable(assessment.isActionable());
        result.setStrategyAction(assessment.getStrategyAction());
        result.setStrongNegative(assessment.isStrongNegative());
        return result;
    }
}
// AI_GENERATE_END -----
