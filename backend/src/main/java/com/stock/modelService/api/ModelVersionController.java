package com.stock.modelService.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.entity.SentimentModelVersion;
import com.stock.modelService.service.ModelVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 情感模型版本管理控制器
 * <p>
 * 提供情感分析模型的版本列表查询、当前版本获取、指定版本回滚、新版本号生成等接口。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Slf4j
@RestController
@RequestMapping("/api/model-sentiment")
@RequiredArgsConstructor
public class ModelVersionController {

    private final ModelVersionService modelVersionService;

    /**
     * 获取版本列表
     *
     * @param status 版本状态筛选（可选）：active/deprecated/rollback
     * @return 版本列表
     */
    @GetMapping("/versions")
    public ResponseDTO<List<ModelVersionDto>> listVersions(
            @RequestParam(required = false) String status) {
        log.info("[ModelVersion] 获取版本列表 | status={}", status);

        try {
            List<SentimentModelVersion> versions = modelVersionService.listVersions(status);
            List<ModelVersionDto> resultList = versions.stream()
                    .map(this::convertToDto)
                    .collect(Collectors.toList());
            return ResponseDTO.success(resultList);
        } catch (Exception e) {
            log.error("获取版本列表失败", e);
            return ResponseDTO.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前活跃版本
     *
     * @return 当前版本信息
     */
    @GetMapping("/versions/current")
    public ResponseDTO<ModelVersionDto> getCurrentVersion() {
        log.info("[ModelVersion] 获取当前活跃版本");

        try {
            SentimentModelVersion currentVersion = modelVersionService.getCurrentVersion();
            if (currentVersion == null) {
                return ResponseDTO.error("暂无活跃版本");
            }
            return ResponseDTO.success(convertToDto(currentVersion));
        } catch (Exception e) {
            log.error("获取当前版本失败", e);
            return ResponseDTO.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 回滚到指定版本
     *
     * @param id 版本ID
     * @return 操作结果
     */
    @PostMapping("/versions/{id}/rollback")
    public ResponseDTO<RollbackResultDto> rollback(@PathVariable String id) {
        log.info("[ModelVersion] 回滚到指定版本 | id={}", id);

        try {
            boolean success = modelVersionService.rollback(id);
            RollbackResultDto result = new RollbackResultDto();
            result.setSuccess(success);
            result.setVersionId(id);
            result.setMessage(success ? "回滚成功" : "回滚失败");

            if (success) {
                SentimentModelVersion current = modelVersionService.getCurrentVersion();
                if (current != null) {
                    result.setCurrentVersion(current.getVersion());
                }
            }

            return ResponseDTO.success(result, result.getMessage());
        } catch (Exception e) {
            log.error("版本回滚失败，id={}", id, e);
            return ResponseDTO.error("回滚失败：" + e.getMessage());
        }
    }

    /**
     * 生成新版本号
     *
     * @param incrementType 增量类型：major/minor/patch，默认minor
     * @return 新版本号
     */
    @GetMapping("/versions/generate")
    public ResponseDTO<VersionGenerateResultDto> generateVersion(
            @RequestParam(defaultValue = "minor") String incrementType) {
        log.info("[ModelVersion] 生成新版本号 | incrementType={}", incrementType);

        try {
            String newVersion = modelVersionService.generateVersion(incrementType);
            VersionGenerateResultDto result = new VersionGenerateResultDto();
            result.setIncrementType(incrementType);
            result.setNewVersion(newVersion);

            SentimentModelVersion current = modelVersionService.getCurrentVersion();
            if (current != null) {
                result.setBaseVersion(current.getVersion());
            }

            return ResponseDTO.success(result);
        } catch (Exception e) {
            log.error("生成版本号失败", e);
            return ResponseDTO.error("生成失败：" + e.getMessage());
        }
    }

    private ModelVersionDto convertToDto(SentimentModelVersion entity) {
        ModelVersionDto dto = new ModelVersionDto();
        dto.setId(entity.getId());
        dto.setVersion(entity.getVersion());
        dto.setDescription(entity.getDescription());
        dto.setModelPath(entity.getModelPath());
        dto.setParentVersion(entity.getParentVersion());
        dto.setAccuracy(entity.getAccuracy());
        dto.setF1Score(entity.getF1Score());
        dto.setSharpeRatio(entity.getSharpeRatio());
        dto.setTrainingSamples(entity.getTrainingSamples());
        dto.setStatus(entity.getStatus());
        dto.setDeployedAt(entity.getDeployedAt());
        dto.setDeprecatedAt(entity.getDeprecatedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    /**
     * 模型版本 DTO
     */
    public static class ModelVersionDto {
        private String id;
        private String version;
        private String description;
        private String modelPath;
        private String parentVersion;
        private Double accuracy;
        private Double f1Score;
        private Double sharpeRatio;
        private Integer trainingSamples;
        private String status;
        private java.time.LocalDateTime deployedAt;
        private java.time.LocalDateTime deprecatedAt;
        private java.time.LocalDateTime createdAt;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getModelPath() {
            return modelPath;
        }

        public void setModelPath(String modelPath) {
            this.modelPath = modelPath;
        }

        public String getParentVersion() {
            return parentVersion;
        }

        public void setParentVersion(String parentVersion) {
            this.parentVersion = parentVersion;
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

        public Double getSharpeRatio() {
            return sharpeRatio;
        }

        public void setSharpeRatio(Double sharpeRatio) {
            this.sharpeRatio = sharpeRatio;
        }

        public Integer getTrainingSamples() {
            return trainingSamples;
        }

        public void setTrainingSamples(Integer trainingSamples) {
            this.trainingSamples = trainingSamples;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public java.time.LocalDateTime getDeployedAt() {
            return deployedAt;
        }

        public void setDeployedAt(java.time.LocalDateTime deployedAt) {
            this.deployedAt = deployedAt;
        }

        public java.time.LocalDateTime getDeprecatedAt() {
            return deprecatedAt;
        }

        public void setDeprecatedAt(java.time.LocalDateTime deprecatedAt) {
            this.deprecatedAt = deprecatedAt;
        }

        public java.time.LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    /**
     * 回滚结果 DTO
     */
    public static class RollbackResultDto {
        private boolean success;
        private String versionId;
        private String currentVersion;
        private String message;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getVersionId() {
            return versionId;
        }

        public void setVersionId(String versionId) {
            this.versionId = versionId;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion = currentVersion;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 版本号生成结果 DTO
     */
    public static class VersionGenerateResultDto {
        private String incrementType;
        private String baseVersion;
        private String newVersion;

        public String getIncrementType() {
            return incrementType;
        }

        public void setIncrementType(String incrementType) {
            this.incrementType = incrementType;
        }

        public String getBaseVersion() {
            return baseVersion;
        }

        public void setBaseVersion(String baseVersion) {
            this.baseVersion = baseVersion;
        }

        public String getNewVersion() {
            return newVersion;
        }

        public void setNewVersion(String newVersion) {
            this.newVersion = newVersion;
        }
    }
}