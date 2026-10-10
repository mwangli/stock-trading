// AI_GENERATE_START -
package com.stock.modelService.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.dto.ModelOperationsDto;
import com.stock.modelService.service.ModelOperationsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型运维管理接口。
 * 提供模型版本、训练记录、受控训练、激活和回滚能力。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ModelOperationsController {

    private final ModelOperationsService modelOperationsService;

    /**
     * 查询模型运维概览和当前门禁状态。
     *
     * @return 模型运维概览
     */
    @GetMapping("/model-operations/overview")
    public ResponseDTO<ModelOperationsDto.OverviewResponse> getOverview() {
        log.info("[ModelOperations] 查询模型运维概览");
        try {
            return ResponseDTO.success(modelOperationsService.getOverview());
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 查询模型运维概览失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 分页查询模型版本。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 模型版本分页结果
     */
    @GetMapping("/model-versions")
    public ResponseDTO<ModelOperationsDto.ModelVersionPageResponse> listVersions(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[ModelOperations] 分页查询模型版本: current={}, pageSize={}", current, pageSize);
        try {
            return ResponseDTO.success(modelOperationsService.listVersions(current, pageSize));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 查询模型版本失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 查询指定模型版本详情。
     *
     * @param versionId 模型版本 ID
     * @return 模型版本详情
     */
    @GetMapping("/model-versions/details/{versionId}")
    public ResponseDTO<ModelOperationsDto.ModelVersionItem> getVersionDetails(
            @PathVariable String versionId) {
        log.info("[ModelOperations] 查询模型版本详情: versionId={}", versionId);
        try {
            return ResponseDTO.success(modelOperationsService.getVersionDetails(versionId));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 查询模型版本详情失败: versionId={}, reason={}",
                    versionId, exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 分页查询模型训练运行记录。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 训练运行分页结果
     */
    @GetMapping("/model-training-runs")
    public ResponseDTO<ModelOperationsDto.TrainingRunPageResponse> listTrainingRuns(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[ModelOperations] 分页查询训练记录: current={}, pageSize={}", current, pageSize);
        try {
            return ResponseDTO.success(modelOperationsService.listTrainingRuns(current, pageSize));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 查询训练记录失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 提交受门禁控制的模型训练任务。
     *
     * @param request 训练参数和操作人
     * @return 训练任务提交结果
     */
    @PostMapping("/model-training-runs/start")
    public ResponseDTO<ModelOperationsDto.OperationResponse> startTraining(
            @RequestBody(required = false) ModelOperationsDto.StartTrainingRequest request) {
        log.info("[ModelOperations] 提交模型训练: days={}, epochs={}, batchSize={}, learningRate={}, operator={}",
                request == null ? null : request.getDays(),
                request == null ? null : request.getEpochs(),
                request == null ? null : request.getBatchSize(),
                request == null ? null : request.getLearningRate(),
                request == null ? null : request.getOperatorName());
        try {
            return ResponseDTO.success(modelOperationsService.startTraining(request));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 提交模型训练失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 激活已通过完整性校验的 READY 模型版本。
     *
     * @param versionId 模型版本 ID
     * @param operatorName 操作人名称
     * @return 激活结果
     */
    @PostMapping("/model-versions/activate/{versionId}")
    public ResponseDTO<ModelOperationsDto.OperationResponse> activateVersion(
            @PathVariable String versionId,
            @RequestParam(required = false) String operatorName) {
        log.info("[ModelOperations] 激活模型版本: versionId={}, operator={}", versionId, operatorName);
        try {
            return ResponseDTO.success(modelOperationsService.activateVersion(versionId, operatorName));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 激活模型版本失败: versionId={}, reason={}",
                    versionId, exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 将模型回滚到上一激活版本。
     *
     * @param modelName 模型逻辑名称
     * @param operatorName 操作人名称
     * @return 回滚结果
     */
    @PostMapping("/model-versions/rollback/{modelName}")
    public ResponseDTO<ModelOperationsDto.OperationResponse> rollback(
            @PathVariable String modelName,
            @RequestParam(required = false) String operatorName) {
        log.info("[ModelOperations] 回滚模型版本: modelName={}, operator={}", modelName, operatorName);
        try {
            return ResponseDTO.success(modelOperationsService.rollback(modelName, operatorName));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 回滚模型版本失败: modelName={}, reason={}",
                    modelName, exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }
}
// AI_GENERATE_END -
