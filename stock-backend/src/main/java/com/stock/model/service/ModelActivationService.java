// AI_GENERATE_START---------
package com.stock.model.service;

import com.stock.model.algorithm.LstmArchitectureType;
import com.stock.model.dto.ModelOperationResponse;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import com.stock.platform.config.CacheNames;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * 基础时序模型自动晋级服务。
 *
 * 负责 CANDIDATE 候选自动激活、旧版本退役和运行时模型预加载。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
public class ModelActivationService {

    private final LstmTrainerService trainerService;
    private final LstmModelRuntimeService modelRuntimeService;
    private final LstmModelRepository modelRepository;
    private final ModelActivationRepository activationRepository;
    private final ModelOperationAuditService auditService;
    private final Executor applicationTaskExecutor;

    /**
     * 创建模型版本激活服务。
     *
     * @param trainerService 模型校验服务
     * @param modelRuntimeService 模型运行时服务
     * @param modelRepository 模型版本仓库
     * @param activationRepository 模型激活指针仓库
     * @param auditService 模型操作审计服务
     * @param applicationTaskExecutor 应用异步执行器
     */
    public ModelActivationService(LstmTrainerService trainerService,
                                  LstmModelRuntimeService modelRuntimeService,
                                  LstmModelRepository modelRepository,
                                  ModelActivationRepository activationRepository,
                                  ModelOperationAuditService auditService,
                                  @Qualifier("applicationTaskExecutor") Executor applicationTaskExecutor) {
        this.trainerService = trainerService;
        this.modelRuntimeService = modelRuntimeService;
        this.modelRepository = modelRepository;
        this.activationRepository = activationRepository;
        this.auditService = auditService;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 自动激活已通过完整性和综合评分校验的候选模型版本。
     *
     * @param versionId 候选模型版本 ID
     * @param operatorName 操作人名称
     * @return 激活结果
     */
    @CacheEvict(cacheNames = CacheNames.ACTIVE_MODEL, allEntries = true)
    public synchronized ModelOperationResponse activateCandidate(String versionId, String operatorName) {
        LstmModelDocument target = modelRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("模型版本不存在"));
        if (!"CANDIDATE".equals(target.getStatus())) {
            throw new IllegalStateException("仅 CANDIDATE 状态的候选版本允许自动激活");
        }
        rejectMasterProductionBaseline(target, "激活");
        trainerService.validateModelVersion(versionId);
        ModelActivationDocument activation = activationRepository
                .findByModelName(target.getModelName()).orElseGet(ModelActivationDocument::new);
        String currentVersionId = activation.getActiveModelVersionId();
        activation.setModelName(target.getModelName());
        activation.setPreviousModelVersionId(currentVersionId);
        activation.setActiveModelVersionId(target.getId());
        activation.setActivatedAt(LocalDateTime.now());
        activationRepository.save(activation);

        markVersionStatus(currentVersionId, "RETIRED");
        target.setStatus("ACTIVE");
        modelRepository.save(target);
        applicationTaskExecutor.execute(modelRuntimeService::preloadActiveModel);
        auditService.record(target.getModelName(), "AUTO_PROMOTE", operatorName, currentVersionId,
                target.getId(), true, "候选版本已自动晋级");
        return ModelOperationResponse.builder()
                .operationType("AUTO_PROMOTE")
                .operationId(target.getId())
                .message("模型版本已自动晋级")
                .build();
    }

    private void rejectMasterProductionBaseline(LstmModelDocument target, String operationName) {
        String metadata = target.getNormalizationParams();
        if (metadata == null || metadata.isBlank()) {
            return;
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(metadata));
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException(operationName + "目标模型元数据无法解析", exception);
        }
        if (LstmArchitectureType.MASTER_TRANSFORMER.name()
                .equals(properties.getProperty("architectureType"))) {
            throw new IllegalStateException("MASTER_TRANSFORMER 只能通过影子预测和时序融合使用，"
                    + "不允许直接" + operationName + "为生产基础模型");
        }
    }

    private void markVersionStatus(String versionId, String status) {
        if (versionId == null || versionId.isBlank()) {
            return;
        }
        Optional<LstmModelDocument> optional = modelRepository.findById(versionId);
        if (optional.isPresent()) {
            LstmModelDocument document = optional.get();
            document.setStatus(status);
            modelRepository.save(document);
        }
    }
}
// AI_GENERATE_END---------
