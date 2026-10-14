// AI_GENERATE_START --
package com.stock.modelService.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.modelService.domain.dto.SentimentEvaluationCase;
import com.stock.modelService.domain.dto.SentimentEvaluationDto;
import com.stock.modelService.domain.entity.SentimentEvaluationEntity;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import com.stock.modelService.domain.vo.SentimentArtifactInspection;
import com.stock.modelService.persistence.SentimentEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 情感模型人工标注评估服务。
 * 评估过程强制使用真实模型，规则降级结果不得进入准确率指标。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentEvaluationService {

    private static final String DATASET_VERSION = "stock-news-manual-v1";
    private static final String DATASET_RESOURCE = "model-evaluation/sentiment-stock-news-v1.json";
    private static final List<String> LABELS = List.of("positive", "neutral", "negative");

    private final SentimentInferenceService inferenceService;
    private final SentimentEvaluationRepository evaluationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 使用内置人工标注集执行一次真实模型评估并保存结果。
     *
     * @return 本次评估指标
     */
    public SentimentEvaluationDto evaluateAndPersist() {
        List<SentimentEvaluationCase> cases = loadCases();
        if (cases.isEmpty()) {
            throw new IllegalStateException("情感模型人工评估集为空");
        }
        SentimentArtifactInspection inspection = inferenceService.getArtifactInspection();
        String modelVersion = inspection.getModelVersion();
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalStateException("情感模型制品没有可追溯版本");
        }

        Map<String, Counts> countsByLabel = new HashMap<>();
        LABELS.forEach(label -> countsByLabel.put(label, new Counts()));
        List<ConfidenceOutcome> confidenceOutcomes = new ArrayList<>();
        List<Long> latencies = new ArrayList<>();
        int correct = 0;
        for (int index = 0; index < cases.size(); index++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("情感模型评估已收到停止请求");
            }
            SentimentEvaluationCase evaluationCase = cases.get(index);
            String expected = normalizeLabel(evaluationCase.getExpectedLabel());
            long startedAt = System.nanoTime();
            SentimentAnalysisResult result = inferenceService.analyzeSentimentRequired(evaluationCase.getText());
            long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
            String predicted = normalizeLabel(result.getLabel());
            boolean matched = expected.equals(predicted);
            if (matched) {
                correct++;
            }
            updateCounts(countsByLabel, expected, predicted);
            confidenceOutcomes.add(new ConfidenceOutcome(result.getConfidence(), matched));
            latencies.add(latencyMs);
            log.info("情感模型评估进度: {}/{}, caseId={}, expected={}, predicted={}, confidence={}, latencyMs={}",
                    index + 1, cases.size(), evaluationCase.getId(), expected, predicted,
                    result.getConfidence(), latencyMs);
        }

        double accuracy = correct / (double) cases.size();
        double macroF1 = LABELS.stream().mapToDouble(label -> f1(countsByLabel.get(label))).average().orElse(0D);
        double negativeRecall = recall(countsByLabel.get("negative"));
        double ece = expectedCalibrationError(confidenceOutcomes);
        double p95Latency = percentile95(latencies);
        String status = macroF1 >= 0.80D && negativeRecall >= 0.85D ? "PASSED"
                : macroF1 >= 0.65D && negativeRecall >= 0.70D ? "OBSERVE" : "REJECTED";
        String conclusion = String.format(Locale.ROOT,
                "人工评估集%d条，准确率%.2f%%、Macro-F1 %.2f%%、负面召回率%.2f%%，结论%s",
                cases.size(), accuracy * 100D, macroF1 * 100D, negativeRecall * 100D, status);
        SentimentEvaluationEntity saved = evaluationRepository.save(SentimentEvaluationEntity.builder()
                .evaluationRunId(UUID.randomUUID().toString()).modelVersion(modelVersion)
                .datasetVersion(DATASET_VERSION).status(status).sampleCount(cases.size()).correctCount(correct)
                .accuracy(accuracy).macroF1(macroF1).negativeRecall(negativeRecall)
                .expectedCalibrationError(ece).p95LatencyMs(p95Latency)
                .conclusion(conclusion).evaluatedAt(LocalDateTime.now()).build());
        log.info("情感模型独立评估完成: runId={}, modelVersion={}, status={}, sampleCount={}",
                saved.getEvaluationRunId(), modelVersion, status, cases.size());
        return toDto(saved);
    }

    /**
     * 查询最近一次独立评估结果。
     *
     * @return 最近评估结果；尚未执行时返回 NOT_EVALUATED
     */
    public SentimentEvaluationDto getLatest() {
        return evaluationRepository.findTopByOrderByEvaluatedAtDesc().map(this::toDto)
                .orElseGet(() -> SentimentEvaluationDto.builder().datasetVersion(DATASET_VERSION)
                        .status("NOT_EVALUATED").conclusion("尚未执行情感模型独立评估任务").build());
    }

    private List<SentimentEvaluationCase> loadCases() {
        try (InputStream input = new ClassPathResource(DATASET_RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("读取情感模型人工评估集失败", exception);
        }
    }

    private void updateCounts(Map<String, Counts> countsByLabel, String expected, String predicted) {
        for (String label : LABELS) {
            Counts counts = countsByLabel.get(label);
            if (label.equals(expected) && label.equals(predicted)) {
                counts.truePositive++;
            } else if (!label.equals(expected) && label.equals(predicted)) {
                counts.falsePositive++;
            } else if (label.equals(expected)) {
                counts.falseNegative++;
            }
        }
    }

    private double f1(Counts counts) {
        double precision = counts.truePositive + counts.falsePositive == 0 ? 0D
                : counts.truePositive / (double) (counts.truePositive + counts.falsePositive);
        double recall = recall(counts);
        return precision + recall == 0D ? 0D : 2D * precision * recall / (precision + recall);
    }

    private double recall(Counts counts) {
        return counts.truePositive + counts.falseNegative == 0 ? 0D
                : counts.truePositive / (double) (counts.truePositive + counts.falseNegative);
    }

    private double expectedCalibrationError(List<ConfidenceOutcome> outcomes) {
        double ece = 0D;
        for (int bin = 0; bin < 10; bin++) {
            int currentBin = bin;
            double lower = currentBin / 10D;
            double upper = (currentBin + 1) / 10D;
            List<ConfidenceOutcome> bucket = outcomes.stream()
                    .filter(item -> item.confidence() >= lower
                            && (currentBin == 9 ? item.confidence() <= upper : item.confidence() < upper))
                    .toList();
            if (bucket.isEmpty()) {
                continue;
            }
            double averageConfidence = bucket.stream().mapToDouble(ConfidenceOutcome::confidence).average().orElse(0D);
            double bucketAccuracy = bucket.stream().filter(ConfidenceOutcome::correct).count() / (double) bucket.size();
            ece += bucket.size() / (double) outcomes.size() * Math.abs(averageConfidence - bucketAccuracy);
        }
        return ece;
    }

    private double percentile95(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95D) - 1);
        return sorted.get(index).doubleValue();
    }

    private String normalizeLabel(String label) {
        String normalized = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        if (!LABELS.contains(normalized)) {
            throw new IllegalStateException("情感模型返回未知标签: " + label);
        }
        return normalized;
    }

    private SentimentEvaluationDto toDto(SentimentEvaluationEntity entity) {
        return SentimentEvaluationDto.builder()
                .evaluationRunId(entity.getEvaluationRunId()).modelVersion(entity.getModelVersion())
                .datasetVersion(entity.getDatasetVersion()).status(entity.getStatus())
                .sampleCount(entity.getSampleCount()).correctCount(entity.getCorrectCount())
                .accuracy(entity.getAccuracy()).macroF1(entity.getMacroF1())
                .negativeRecall(entity.getNegativeRecall())
                .expectedCalibrationError(entity.getExpectedCalibrationError())
                .p95LatencyMs(entity.getP95LatencyMs()).conclusion(entity.getConclusion())
                .evaluatedAt(entity.getEvaluatedAt()).build();
    }

    private static final class Counts {
        private int truePositive;
        private int falsePositive;
        private int falseNegative;
    }

    private record ConfidenceOutcome(double confidence, boolean correct) { }
}
// AI_GENERATE_END --
