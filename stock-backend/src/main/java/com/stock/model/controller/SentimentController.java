// AI_GENERATE_START----------
package com.stock.model.controller;

import com.stock.platform.dto.ResponseDTO;
import com.stock.model.dto.SentimentAnalyzeRequestDto;
import com.stock.model.dto.SentimentAnalyzeResultDto;
import com.stock.model.dto.SentimentHealthDto;
import com.stock.model.vo.SentimentArtifactInspection;
import com.stock.model.vo.SentimentArtifactManifest;
import com.stock.model.vo.SentimentAnalysisResult;
import com.stock.model.service.SentimentInferenceService;
import com.stock.model.service.SentimentAggregationService;
import com.stock.model.service.MengziModelRuntimeService;
import com.stock.model.vo.SentimentAggregateResult;
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
    private final SentimentAggregationService aggregationService;
    private final MengziModelRuntimeService mengziModelRuntimeService;

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
        java.nio.file.Path mengziEncoderPath = null;
        String mengziEncoderError = null;
        try {
            mengziEncoderPath = mengziModelRuntimeService.validateEncoderArtifact();
        } catch (RuntimeException exception) {
            mengziEncoderError = exception.getMessage();
        }
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
                .mengziEncoderVerified(mengziEncoderPath != null)
                .mengziEncoderPath(mengziEncoderPath == null ? null : mengziEncoderPath.toString())
                .mengziClassifierStatus(mengziModelRuntimeService.isLoaded()
                        ? "CLASSIFIER_LOADED" : "WAITING_FOR_GOLD_LABEL_TRAINING")
                .lastError(error != null ? error : mengziEncoderError)
                .build());
    }

    private SentimentAnalyzeResultDto toDto(SentimentAnalysisResult result) {
        return SentimentAnalyzeResultDto.builder()
                .success(true)
                .label(result.getLabel())
                .score(result.getScore())
                .normalizedScore(result.getNormalizedScore())
                .confidence(result.getConfidence())
                .calibratedConfidence(result.getCalibratedConfidence())
                .entropy(result.getEntropy())
                .probabilityMargin(result.getProbabilityMargin())
                .abstained(result.isAbstained())
                .chunkCount(result.getChunkCount())
                .textCoverage(result.getTextCoverage())
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
// AI_GENERATE_END----------
