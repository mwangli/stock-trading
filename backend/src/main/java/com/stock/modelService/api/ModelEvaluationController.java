package com.stock.modelService.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.entity.SentimentEvaluation;
import com.stock.modelService.service.AutoLabelService;
import com.stock.modelService.service.ModelEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 情感模型评估控制器
 * <p>
 * 提供情感分析模型的手动评估触发、评估结果查询、标注数量统计等接口。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Slf4j
@RestController
@RequestMapping("/api/model-sentiment")
@RequiredArgsConstructor
public class ModelEvaluationController {

    private final ModelEvaluationService modelEvaluationService;
    private final AutoLabelService autoLabelService;

    /**
     * 手动触发模型评估
     *
     * @return 评估结果
     */
    @PostMapping("/evaluate")
    public ResponseDTO<ModelEvaluationService.EvaluationResult> triggerEvaluation() {
        log.info("[ModelEvaluation] 手动触发模型评估");
        try {
            ModelEvaluationService.EvaluationResult result = modelEvaluationService.evaluateModel("manual");
            if (result.isSuccess()) {
                return ResponseDTO.success(result, "评估完成");
            } else {
                return ResponseDTO.error(result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("手动触发模型评估失败", e);
            return ResponseDTO.error("评估失败：" + e.getMessage());
        }
    }

    /**
     * 获取评估结果列表
     *
     * @param modelVersion 模型版本筛选（可选）
     * @param limit        返回结果数量限制，默认10
     * @return 评估结果列表
     */
    @GetMapping("/evaluation/results")
    public ResponseDTO<List<EvaluationResultDto>> getEvaluationResults(
            @RequestParam(required = false) String modelVersion,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("[ModelEvaluation] 获取评估结果列表 | modelVersion={}, limit={}", modelVersion, limit);

        try {
            List<SentimentEvaluation> evaluations;

            if (modelVersion != null && !modelVersion.trim().isEmpty()) {
                evaluations = modelEvaluationService.findByModelVersion(modelVersion);
            } else {
                evaluations = modelEvaluationService.findLatestEvaluations(limit);
            }

            List<EvaluationResultDto> resultList = evaluations.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());

            return ResponseDTO.success(resultList);
        } catch (Exception e) {
            log.error("获取评估结果列表失败", e);
            return ResponseDTO.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取最新评估结果
     *
     * @return 最新评估结果
     */
    @GetMapping("/evaluation/latest")
    public ResponseDTO<EvaluationResultDto> getLatestEvaluation() {
        log.info("[ModelEvaluation] 获取最新评估结果");
        try {
            ModelEvaluationService.EvaluationResult latestResult = modelEvaluationService.getLatestResult();
            if (latestResult == null) {
                return ResponseDTO.error("暂无评估记录");
            }
            return ResponseDTO.success(convertFromResult(latestResult));
        } catch (Exception e) {
            log.error("获取最新评估结果失败", e);
            return ResponseDTO.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取指定状态的标注数量
     *
     * @param status 标注状态：pending/validated/used/discarded，默认validated
     * @return 标注数量
     */
    @GetMapping("/labels/count")
    public ResponseDTO<LabelCountDto> getLabelCount(
            @RequestParam(required = false, defaultValue = "validated") String status) {
        log.info("[ModelEvaluation] 获取标注数量 | status={}", status);

        try {
            long count;
            if ("all".equalsIgnoreCase(status)) {
                count = autoLabelService.getAvailableLabelCount();
            } else {
                count = autoLabelService.getLabelCountByStatus(status);
            }

            LabelCountDto dto = new LabelCountDto();
            dto.setStatus(status);
            dto.setCount(count);

            return ResponseDTO.success(dto);
        } catch (Exception e) {
            log.error("获取标注数量失败", e);
            return ResponseDTO.error("查询失败：" + e.getMessage());
        }
    }

    private EvaluationResultDto convertToDto(SentimentEvaluation entity) {
        EvaluationResultDto dto = new EvaluationResultDto();
        dto.setId(entity.getId());
        dto.setModelVersion(entity.getModelVersion());
        dto.setAccuracy(entity.getAccuracy());
        dto.setF1Score(entity.getF1Score());
        dto.setPrecision(entity.getPrecision());
        dto.setRecall(entity.getRecall());
        dto.setRocAuc(entity.getRocAuc());
        dto.setSharpeRatio(entity.getSharpeRatio());
        dto.setDirectionAccuracy(entity.getDirectionAccuracy());
        dto.setMaxDrawdown(entity.getMaxDrawdown());
        dto.setSampleCount(entity.getSampleCount());
        dto.setThresholdStatus(entity.getThresholdStatus());
        dto.setTriggerSource(entity.getTriggerSource());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private EvaluationResultDto convertFromResult(ModelEvaluationService.EvaluationResult result) {
        EvaluationResultDto dto = new EvaluationResultDto();
        dto.setModelVersion(result.getModelVersion());
        dto.setAccuracy(result.getAccuracy());
        dto.setF1Score(result.getF1Score());
        dto.setPrecision(result.getPrecision());
        dto.setRecall(result.getRecall());
        dto.setRocAuc(result.getRocAuc());
        dto.setSharpeRatio(result.getSharpeRatio());
        dto.setDirectionAccuracy(result.getDirectionAccuracy());
        dto.setMaxDrawdown(result.getMaxDrawdown());
        dto.setSampleCount(result.getSampleCount());
        dto.setThresholdStatus(result.getThresholdStatus());
        dto.setTriggerSource(result.getTriggerSource());
        dto.setCreatedAt(result.getCreatedAt());
        dto.setShouldFineTune(result.isShouldFineTune());
        return dto;
    }

    /**
     * 评估结果 DTO
     */
    public static class EvaluationResultDto {
        private String id;
        private String modelVersion;
        private Double accuracy;
        private Double f1Score;
        private Double precision;
        private Double recall;
        private Double rocAuc;
        private Double sharpeRatio;
        private Double directionAccuracy;
        private Double maxDrawdown;
        private Integer sampleCount;
        private String thresholdStatus;
        private String triggerSource;
        private java.time.LocalDateTime createdAt;
        private Boolean shouldFineTune;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getModelVersion() {
            return modelVersion;
        }

        public void setModelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
        }

        public Double getAccuracy() {
            return accuracy;
        }

        public void setAccuracy(Double accuracy) {
            this.accuracy = accuracy;
        }

        public Double getF1Score() {
            return f1Score;
        }

        public void setF1Score(Double f1Score) {
            this.f1Score = f1Score;
        }

        public Double getPrecision() {
            return precision;
        }

        public void setPrecision(Double precision) {
            this.precision = precision;
        }

        public Double getRecall() {
            return recall;
        }

        public void setRecall(Double recall) {
            this.recall = recall;
        }

        public Double getRocAuc() {
            return rocAuc;
        }

        public void setRocAuc(Double rocAuc) {
            this.rocAuc = rocAuc;
        }

        public Double getSharpeRatio() {
            return sharpeRatio;
        }

        public void setSharpeRatio(Double sharpeRatio) {
            this.sharpeRatio = sharpeRatio;
        }

        public Double getDirectionAccuracy() {
            return directionAccuracy;
        }

        public void setDirectionAccuracy(Double directionAccuracy) {
            this.directionAccuracy = directionAccuracy;
        }

        public Double getMaxDrawdown() {
            return maxDrawdown;
        }

        public void setMaxDrawdown(Double maxDrawdown) {
            this.maxDrawdown = maxDrawdown;
        }

        public Integer getSampleCount() {
            return sampleCount;
        }

        public void setSampleCount(Integer sampleCount) {
            this.sampleCount = sampleCount;
        }

        public String getThresholdStatus() {
            return thresholdStatus;
        }

        public void setThresholdStatus(String thresholdStatus) {
            this.thresholdStatus = thresholdStatus;
        }

        public String getTriggerSource() {
            return triggerSource;
        }

        public void setTriggerSource(String triggerSource) {
            this.triggerSource = triggerSource;
        }

        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Boolean getShouldFineTune() {
            return shouldFineTune;
        }

        public void setShouldFineTune(Boolean shouldFineTune) {
            this.shouldFineTune = shouldFineTune;
        }
    }

    /**
     * 标注数量 DTO
     */
    public static class LabelCountDto {
        private String status;
        private long count;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }
}