package com.stock.modelService.controller;

import com.stock.modelService.dto.SentimentTrainingRequest;
import com.stock.modelService.inference.SentimentInference;
import com.stock.modelService.service.SentimentTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 情感分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/models/sentiment")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentTrainerService trainerService;
    private final SentimentInference sentimentInference;

    /**
     * 训练情感分析模型
     */
    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> trainModel(@RequestBody(required = false) SentimentTrainingRequest request) {
        log.info("收到情感分析训练请求");

        if (request == null) {
            request = SentimentTrainingRequest.builder()
                    .numSamples(500)
                    .autoLabel(true)
                    .build();
        }

        try {
            var result = trainerService.trainModel(request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("message", result.getMessage());
            response.put("trainingId", result.getTrainingId());
            response.put("epochs", result.getEpochs());
            response.put("trainLoss", result.getTrainLoss());
            response.put("valAccuracy", result.getValAccuracy());
            response.put("modelPath", result.getModelPath());
            response.put("trainSamples", result.getTrainSamples());
            response.put("valSamples", result.getValSamples());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("情感分析训练失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "训练失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 查询训练状态
     */
    @GetMapping("/status/{trainingId}")
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
     * 分析单条文本情感
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeText(@RequestBody Map<String, String> requestBody) {
        String text = requestBody.get("text");

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "文本不能为空"
            ));
        }

        try {
            Map<String, Object> result = sentimentInference.analyzeWithDetails(text);
            result.put("success", true);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("情感分析失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "分析失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 批量分析文本情感
     */
    @PostMapping("/analyze/batch")
    public ResponseEntity<Map<String, Object>> analyzeBatch(@RequestBody Map<String, Object> requestBody) {
        Object textsObj = requestBody.get("texts");

        if (!(textsObj instanceof java.util.List)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "texts 必须是数组"
            ));
        }

        try {
            java.util.List<?> texts = (java.util.List<?>) textsObj;
            java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

            for (Object textObj : texts) {
                String text = textObj.toString();
                Map<String, Object> result = sentimentInference.analyzeWithDetails(text);
                result.put("index", results.size());
                results.add(result);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", results.size());
            response.put("results", results);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("批量情感分析失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "分析失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 模型健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "Sentiment Analysis Service");
        response.put("modelLoaded", sentimentInference.isLoaded());
        response.put("lastLoadedTime", sentimentInference.getLastLoadedTime());
        return ResponseEntity.ok(response);
    }

    /**
     * 重新加载模型
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadModel() {
        try {
            sentimentInference.reloadModel();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "模型重新加载成功");
            response.put("modelLoaded", sentimentInference.isLoaded());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("重新加载模型失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "重新加载失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 下载预训练模型
     */
    @PostMapping("/download")
    public ResponseEntity<Map<String, Object>> downloadModel() {
        try {
            String modelPath = trainerService.downloadPretrainedModel();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "预训练模型下载成功");
            response.put("modelPath", modelPath);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("下载预训练模型失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "下载失败：" + e.getMessage()
            ));
        }
    }
}