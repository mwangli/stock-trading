// AI_GENERATE_START-----
package com.stock.model.controller;

import com.stock.base.api.ResponseDTO;
import com.stock.model.dto.ModelOperationResponse;
import com.stock.model.dto.ModelOverviewResponse;
import com.stock.model.dto.ModelTrainingRequest;
import com.stock.model.dto.ModelTrainingRunPageResponse;
import com.stock.model.dto.ModelVersionPageResponse;
import com.stock.model.dto.ModelVersionResponse;
import com.stock.model.service.ModelActivationService;
import com.stock.model.service.ModelOperationsQueryService;
import com.stock.model.service.ModelOperationsService;
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
 * 提供模型版本、训练记录、模型训练、激活和回滚能力。
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
    private final ModelOperationsQueryService modelOperationsQueryService;
    private final ModelActivationService modelActivationService;

    /**
     * 查询模型运维概览和当前运行状态。
     *
     * @return 模型运维概览
     */
    @GetMapping("/modelOperations/overview")
    public ResponseDTO<ModelOverviewResponse> getOverview() {
        log.info("[ModelOperations] 查询模型运维概览");
        try {
            return ResponseDTO.success(modelOperationsQueryService.getOverview());
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
    @GetMapping("/modelVersions")
    public ResponseDTO<ModelVersionPageResponse> listVersions(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[ModelOperations] 分页查询模型版本: current={}, pageSize={}", current, pageSize);
        try {
            return ResponseDTO.success(modelOperationsQueryService.listVersions(current, pageSize));
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
    @GetMapping("/modelVersions/details/{versionId}")
    public ResponseDTO<ModelVersionResponse> getVersionDetails(
            @PathVariable String versionId) {
        log.info("[ModelOperations] 查询模型版本详情: versionId={}", versionId);
        try {
            return ResponseDTO.success(modelOperationsQueryService.getVersionDetails(versionId));
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
    @GetMapping("/modelTrainingRuns")
    public ResponseDTO<ModelTrainingRunPageResponse> listTrainingRuns(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[ModelOperations] 分页查询训练记录: current={}, pageSize={}", current, pageSize);
        try {
            return ResponseDTO.success(modelOperationsQueryService.listTrainingRuns(current, pageSize));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 查询训练记录失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 提交模型训练任务。
     *
     * @param request 训练参数和操作人
     * @return 训练任务提交结果
     */
    @PostMapping("/modelTrainingRuns/start")
    public ResponseDTO<ModelOperationResponse> startTraining(
            @RequestBody(required = false) ModelTrainingRequest request) {
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
     * 预先构建 LSTM 训练数据快照，供后续训练复用。
     *
     * @param request 快照构建参数
     * @return 快照构建结果
     */
    @PostMapping("/modelTrainingSnapshots/build")
    public ResponseDTO<ModelOperationResponse> buildTrainingSnapshot(
            @RequestBody(required = false) ModelTrainingRequest request) {
        log.info("[ModelOperations] 构建 LSTM 训练数据快照: days={}, operator={}",
                request == null ? null : request.getDays(),
                request == null ? null : request.getOperatorName());
        try {
            return ResponseDTO.success(modelOperationsService.prepareTrainingSnapshot(request));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 构建训练数据快照失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 停止排队或运行中的模型训练任务。
     *
     * @param runId 训练运行 ID
     * @param operatorName 操作人名称
     * @return 停止结果
     */
    @PostMapping("/modelTrainingRuns/stop/{runId}")
    public ResponseDTO<ModelOperationResponse> stopTraining(
            @PathVariable String runId, @RequestParam(required = false) String operatorName) {
        log.info("[ModelOperations] 停止模型训练: runId={}, operator={}", runId, operatorName);
        try {
            return ResponseDTO.success(modelOperationsService.stopTraining(runId, operatorName));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 停止模型训练失败: runId={}, reason={}", runId, exception.getMessage());
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
    @PostMapping("/modelVersions/activate/{versionId}")
    public ResponseDTO<ModelOperationResponse> activateVersion(
            @PathVariable String versionId,
            @RequestParam(required = false) String operatorName) {
        log.info("[ModelOperations] 激活模型版本: versionId={}, operator={}", versionId, operatorName);
        try {
            return ResponseDTO.success(modelActivationService.activateVersion(versionId, operatorName));
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
    @PostMapping("/modelVersions/rollback/{modelName}")
    public ResponseDTO<ModelOperationResponse> rollback(
            @PathVariable String modelName,
            @RequestParam(required = false) String operatorName) {
        log.info("[ModelOperations] 回滚模型版本: modelName={}, operator={}", modelName, operatorName);
        try {
            return ResponseDTO.success(modelActivationService.rollback(modelName, operatorName));
        } catch (RuntimeException exception) {
            log.warn("[ModelOperations] 回滚模型版本失败: modelName={}, reason={}",
                    modelName, exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }
}
// AI_GENERATE_END-----
