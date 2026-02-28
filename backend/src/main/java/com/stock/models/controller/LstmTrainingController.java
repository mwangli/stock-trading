package com.stock.models.controller;

import com.stock.models.dto.TrainingRequest;
import com.stock.models.service.LstmTrainerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LSTM 模型训练控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/models/lstm")
@RequiredArgsConstructor
@Tag(name = "LSTM 模型训练", description = "LSTM 股票预测模型训练相关接口")
public class LstmTrainingController {

    private final LstmTrainerService trainerService;

    /**
     * 启动 LSTM 模型训练
     */
    @PostMapping("/train")
    @Operation(summary = "训练 LSTM 模型", description = "使用历史数据训练 LSTM 股票预测模型")
    public ResponseEntity<Map<String, Object>> trainModel(@RequestBody TrainingRequest request) {
        log.info("收到 LSTM 训练请求：股票代码={}, 天数={}, 轮次={}", 
                request.getStockCodes(), request.getDays(), request.getEpochs());

        // 参数验证
        if (request.getStockCodes() == null || request.getStockCodes().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "股票代码不能为空"
            ));
        }

        if (request.getDays() == null || request.getDays() < 60) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "训练数据天数至少为 60 天"
            ));
        }

        // 异步启动训练（简化实现，实际应该使用异步任务）
        try {
            var result = trainerService.trainModel(
                request.getStockCodes(),
                request.getDays(),
                request.getEpochs(),
                request.getBatchSize(),
                request.getLearningRate()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("epochs", result.getEpochs());
            response.put("trainLoss", result.getTrainLoss());
            response.put("valLoss", result.getValLoss());
            response.put("modelPath", result.getModelPath());
            response.put("trainSamples", result.getTrainSamples());
            response.put("valSamples", result.getValSamples());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("LSTM 训练失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "message", "训练失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 获取训练状态
     */
    @GetMapping("/status/{trainingId}")
    @Operation(summary = "获取训练状态", description = "查询 LSTM 模型训练的进度状态")
    public ResponseEntity<Map<String, Object>> getTrainingStatus(@PathVariable String trainingId) {
        var status = trainerService.getTrainingStatus(trainingId);
        
        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("trainingId", trainingId);
        response.put("status", status.getStatus());
        response.put("progress", status.getProgress());
        response.put("currentEpoch", status.getCurrentEpoch());
        response.put("totalEpochs", status.getTotalEpochs());

        return ResponseEntity.ok(response);
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查 LSTM 训练服务是否可用")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "LSTM Training Service");
        return ResponseEntity.ok(response);
    }
}