// AI_GENERATE_START --
package com.stock.modelService.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.modelService.domain.dto.ModelPredictionAnalysisDto;
import com.stock.modelService.service.ModelPredictionAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型预测持久化和能力评估接口。
 * 提供全市场预测、实际结果回填、批次查询和预测对比能力。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Slf4j
@RestController
@RequestMapping("/api/modelPredictions")
@RequiredArgsConstructor
public class ModelPredictionController {

    private final ModelPredictionAnalysisService predictionAnalysisService;

    /**
     * 使用当前激活模型发起一轮全市场预测。
     *
     * @param request 预测操作参数
     * @return 异步预测批次信息
     */
    @PostMapping("/run")
    public ResponseDTO<ModelPredictionAnalysisDto.OperationResponse> runPrediction(
            @RequestBody(required = false) ModelPredictionAnalysisDto.RunPredictionRequest request) {
        log.info("[ModelPrediction] 发起全市场预测: operator={}",
                request == null ? null : request.getOperatorName());
        try {
            return ResponseDTO.success(predictionAnalysisService.startPrediction(request));
        } catch (RuntimeException exception) {
            log.warn("[ModelPrediction] 发起全市场预测失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 使用原模型版本和历史基准日重新生成指定预测批次。
     *
     * @param request 历史预测重算请求
     * @return 新建的历史预测批次信息
     */
    @PostMapping("/rerun")
    public ResponseDTO<ModelPredictionAnalysisDto.OperationResponse> rerunPrediction(
            @RequestBody ModelPredictionAnalysisDto.RerunPredictionRequest request) {
        log.info("[ModelPrediction] 重算历史预测批次: batchId={}, operator={}",
                request == null ? null : request.getBatchId(),
                request == null ? null : request.getOperatorName());
        try {
            return ResponseDTO.success(predictionAnalysisService.rerunPrediction(request));
        } catch (RuntimeException exception) {
            log.warn("[ModelPrediction] 重算历史预测批次失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 回填实际行情并刷新模型能力指标。
     *
     * @param request 评估请求
     * @return 评估操作结果
     */
    @PostMapping("/evaluate")
    public ResponseDTO<ModelPredictionAnalysisDto.OperationResponse> evaluatePrediction(
            @RequestBody(required = false) ModelPredictionAnalysisDto.EvaluatePredictionRequest request) {
        log.info("[ModelPrediction] 刷新预测实际结果: batchId={}",
                request == null ? null : request.getBatchId());
        try {
            return ResponseDTO.success(predictionAnalysisService.evaluatePredictions(request));
        } catch (RuntimeException exception) {
            log.warn("[ModelPrediction] 刷新预测实际结果失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 分页查询模型预测批次。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 预测批次分页结果
     */
    @GetMapping("/batches")
    public ResponseDTO<ModelPredictionAnalysisDto.BatchPageResponse> listBatches(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize) {
        log.info("[ModelPrediction] 分页查询预测批次: current={}, pageSize={}", current, pageSize);
        try {
            return ResponseDTO.success(predictionAnalysisService.listBatches(current, pageSize));
        } catch (RuntimeException exception) {
            log.warn("[ModelPrediction] 查询预测批次失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 分页查询模型预测与实际结果对比。
     *
     * @param batchId 预测批次 ID；为空时使用最新批次
     * @param evaluationStatus 评估状态筛选
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 预测明细分页结果
     */
    @GetMapping("/results")
    public ResponseDTO<ModelPredictionAnalysisDto.PredictionPageResponse> listResults(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String evaluationStatus,
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int pageSize) {
        log.info("[ModelPrediction] 查询预测结果: batchId={}, status={}, current={}, pageSize={}",
                batchId, evaluationStatus, current, pageSize);
        try {
            return ResponseDTO.success(predictionAnalysisService.listPredictions(
                    batchId, evaluationStatus, current, pageSize));
        } catch (RuntimeException exception) {
            log.warn("[ModelPrediction] 查询预测结果失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }

    /**
     * 查询指定或最新批次的模型能力概要。
     *
     * @param batchId 预测批次 ID；为空时使用最新批次
     * @return 模型能力指标
     */
    @GetMapping("/ability")
    public ResponseDTO<ModelPredictionAnalysisDto.AbilityResponse> getAbility(
            @RequestParam(required = false) String batchId) {
        log.info("[ModelPrediction] 查询模型能力概要: batchId={}", batchId);
        try {
            return ResponseDTO.success(predictionAnalysisService.getAbility(batchId));
        } catch (RuntimeException exception) {
            log.warn("[ModelPrediction] 查询模型能力概要失败: {}", exception.getMessage());
            return ResponseDTO.error(exception.getMessage());
        }
    }
}
// AI_GENERATE_END --
