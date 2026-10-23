// AI_GENERATE_START---
package com.stock.model.service;

import com.stock.model.dto.ModelOperationResponse;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * 模型版本激活服务。
 *
 * 负责 READY 候选版本激活、上一版本回滚、版本状态迁移和运行时模型预加载。
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
     * 激活已通过完整性校验的候选模型版本。
     *
     * @param versionId 候选模型版本 ID
     * @param operatorName 操作人名称
     * @return 激活结果
     */
    public synchronized ModelOperationResponse activateVersion(String versionId, String operatorName) {
        LstmModelDocument target = modelRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("模型版本不存在"));
        if (!"READY".equals(target.getStatus())) {
            throw new IllegalStateException("仅 READY 状态的候选版本允许激活");
        }
        trainerService.validateModelVersion(versionId);
        ModelActivationDocument activation = activationRepository
                .findByModelName(target.getModelName()).orElseGet(ModelActivationDocument::new);
        String currentVersionId = activation.getActiveModelVersionId();
        activation.setModelName(target.getModelName());
        activation.setPreviousModelVersionId(currentVersionId);
        activation.setActiveModelVersionId(target.getId());
        activation.setActivatedAt(LocalDateTime.now());
        activationRepository.save(activation);

        markVersionStatus(currentVersionId, "ARCHIVED");
        target.setStatus("ACTIVE");
        modelRepository.save(target);
        applicationTaskExecutor.execute(modelRuntimeService::preloadActiveModel);
        auditService.record(target.getModelName(), "ACTIVATE", operatorName, currentVersionId,
                target.getId(), true, "候选版本已激活");
        return ModelOperationResponse.builder()
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
    public synchronized ModelOperationResponse rollback(String modelName, String operatorName) {
        ModelActivationDocument activation = activationRepository.findByModelName(modelName)
                .orElseThrow(() -> new IllegalStateException("模型尚未建立激活指针"));
        String targetVersionId = activation.getPreviousModelVersionId();
        if (targetVersionId == null || targetVersionId.isBlank()) {
            throw new IllegalStateException("当前模型没有可回滚的上一版本");
        }
        trainerService.validateModelVersion(targetVersionId);
        String currentVersionId = activation.getActiveModelVersionId();
        activation.setActiveModelVersionId(targetVersionId);
        activation.setPreviousModelVersionId(currentVersionId);
        activation.setActivatedAt(LocalDateTime.now());
        activationRepository.save(activation);

        markVersionStatus(currentVersionId, "ARCHIVED");
        markVersionStatus(targetVersionId, "ACTIVE");
        applicationTaskExecutor.execute(modelRuntimeService::preloadActiveModel);
        auditService.record(modelName, "ROLLBACK", operatorName, currentVersionId,
                targetVersionId, true, "模型已回滚到上一版本");
        return ModelOperationResponse.builder()
                .operationType("ROLLBACK")
                .operationId(targetVersionId)
                .message("模型已回滚到上一版本")
                .build();
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
// AI_GENERATE_END---
