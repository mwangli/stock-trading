package com.stock.modelService.api;

import com.stock.modelService.domain.param.SentimentTrainingRequest;
import com.stock.modelService.domain.dto.SentimentAnalyzeRequestDto;
import com.stock.modelService.domain.dto.SentimentBatchAnalyzeRequestDto;
import com.stock.modelService.domain.dto.SentimentAnalyzeByStockRequestDto;
import com.stock.modelService.domain.dto.SentimentAnalyzeResultDto;
import com.stock.modelService.domain.dto.SentimentPredictRequestDto;
import com.stock.common.dto.ResponseDTO;
import com.stock.modelService.domain.dto.SentimentBatchAnalyzeResultDto;
import com.stock.modelService.domain.dto.SentimentHealthDto;
import com.stock.modelService.domain.dto.SentimentReloadDto;
import com.stock.modelService.domain.dto.SentimentDownloadDto;
import com.stock.modelService.service.SentimentTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * 情感分析控制器
 * <p>
 * 提供情感分析模型训练、单条/批量文本分析、模型健康检查、重载等接口。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/model-sentiment")
@RequiredArgsConstructor
public class SentimentController {

    private final SentimentTrainerService trainerService;
    private Double castDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Double> castProbabilities(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Double> result = new HashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                String key = entry.getKey().toString();
                Object v = entry.getValue();
                result.put(key, v instanceof Number ? ((Number) v).doubleValue() : castDouble(v));
            }
            return result;
        }
        return Map.of();
    }

    /**
     * 训练情感分析模型
     */
    @PostMapping("/train")
    public ResponseEntity<com.stock.modelService.domain.vo.SentimentTrainingResponse> trainModel(
            @RequestBody(required = false) SentimentTrainingRequest request) {
        log.info("收到情感分析训练请求");

        if (request == null) {
            request = SentimentTrainingRequest.builder()
                    .numSamples(500)
                    .autoLabel(true)
                    .build();
        }

        try {
            var result = trainerService.trainModel(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("情感分析训练失败", e);
            com.stock.modelService.domain.vo.SentimentTrainingResponse error =
                    com.stock.modelService.domain.vo.SentimentTrainingResponse.builder()
                            .success(false)
                            .message("训练失败：" + e.getMessage())
                            .build();
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * 查询训练状态
     *
     * @param trainingId 训练任务 ID
     */
    @GetMapping("/status/{trainingId}")
    public ResponseEntity<SentimentTrainerService.TrainingStatus> getTrainingStatus(@PathVariable String trainingId) {
        log.info("[Sentiment] 查询训练状态 | trainingId={}", trainingId);
        var status = trainerService.getTrainingStatus(trainingId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    /**
     * 分析单条文本情感
     */
    @PostMapping("/analyze")
    public ResponseEntity<SentimentAnalyzeResultDto> analyzeText(@RequestBody SentimentAnalyzeRequestDto requestBody) {
        String text = requestBody != null ? requestBody.getText() : null;
        log.info("[Sentiment] 单条文本情感分析 | textLength={}", text != null ? text.length() : 0);

        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    SentimentAnalyzeResultDto.builder()
                            .success(false)
                            .message("文本不能为空")
                            .build()
            );
        }

        try {
            Map<String, Object> result = trainerService.analyzeSentimentWithDetails(text);
            SentimentAnalyzeResultDto dto = SentimentAnalyzeResultDto.builder()
                    .success(true)
                    .label((String) result.getOrDefault("label", null))
                    .score(castDouble(result.get("score")))
                    .normalizedScore(castDouble(result.get("normalizedScore")))
                    .confidence(castDouble(result.get("confidence")))
                    .probabilities(castProbabilities(result.get("probabilities")))
                    .text((String) result.getOrDefault("text", ""))
                    .modelLoaded((Boolean) result.getOrDefault("modelLoaded", Boolean.FALSE))
                    .build();

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("情感分析失败", e);
            return ResponseEntity.internalServerError().body(
                    SentimentAnalyzeResultDto.builder()
                            .success(false)
                            .message("分析失败：" + e.getMessage())
                            .build()
            );
        }
    }

    /**
     * 批量分析文本情感
     */
    @PostMapping("/analyze/batch")
    public ResponseEntity<SentimentBatchAnalyzeResultDto> analyzeBatch(
            @RequestBody SentimentBatchAnalyzeRequestDto requestBody) {
        List<String> texts = requestBody != null ? requestBody.getTexts() : null;
        log.info("[Sentiment] 批量情感分析 | count={}", texts != null ? texts.size() : 0);

        if (texts == null || texts.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    SentimentBatchAnalyzeResultDto.builder()
                            .success(false)
                            .message("texts 不能为空")
                            .count(0)
                            .results(List.of())
                            .build()
            );
        }

        try {
            List<SentimentAnalyzeResultDto> results = new java.util.ArrayList<>();

            for (String text : texts) {
                Map<String, Object> result = trainerService.analyzeSentimentWithDetails(text);
                SentimentAnalyzeResultDto dto = SentimentAnalyzeResultDto.builder()
                        .success(true)
                        .label((String) result.getOrDefault("label", null))
                        .score(castDouble(result.get("score")))
                        .normalizedScore(castDouble(result.get("normalizedScore")))
                        .confidence(castDouble(result.get("confidence")))
                        .probabilities(castProbabilities(result.get("probabilities")))
                        .text((String) result.getOrDefault("text", ""))
                        .modelLoaded((Boolean) result.getOrDefault("modelLoaded", Boolean.FALSE))
                        .build();
                results.add(dto);
            }

            SentimentBatchAnalyzeResultDto response = SentimentBatchAnalyzeResultDto.builder()
                    .success(true)
                    .message("分析完成")
                    .count(results.size())
                    .results(results)
                    .build();

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("批量情感分析失败", e);
            return ResponseEntity.internalServerError().body(
                    SentimentBatchAnalyzeResultDto.builder()
                            .success(false)
                            .message("分析失败：" + e.getMessage())
                            .count(0)
                            .results(List.of())
                            .build()
            );
        }
    }

    /**
     * 模型健康检查
     */
    @GetMapping("/health")
    public ResponseEntity<SentimentHealthDto> health() {
        log.info("[Sentiment] 模型健康检查");
        SentimentHealthDto dto = SentimentHealthDto.builder()
                .status("UP")
                .service("Sentiment Analysis Service")
                .modelLoaded(trainerService.isModelLoaded())
                .lastLoadedTime(trainerService.getLastLoadedTime())
                .build();
        return ResponseEntity.ok(dto);
    }

    /**
     * 重新加载模型
     */
    @PostMapping("/reload")
    public ResponseEntity<SentimentReloadDto> reloadModel() {
        log.info("[Sentiment] 重新加载模型");
        try {
            trainerService.unloadModel();
            trainerService.loadModel();

            SentimentReloadDto dto = SentimentReloadDto.builder()
                    .success(true)
                    .message("模型重新加载成功")
                    .modelLoaded(trainerService.isModelLoaded())
                    .build();

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("重新加载模型失败", e);
            SentimentReloadDto dto = SentimentReloadDto.builder()
                    .success(false)
                    .message("重新加载失败：" + e.getMessage())
                    .modelLoaded(false)
                    .build();
            return ResponseEntity.internalServerError().body(dto);
        }
    }

    /**
     * 情感分析模型预测
     * <p>
     * 输入股票代码和新闻内容，输出情感分析结果（标签、得分、置信度等）。
     * 使用 ResponseDTO 统一封装返回。
     * </p>
     *
     * @param requestBody 请求体，必须包含 stockCode（股票代码）和 news（新闻内容）
     * @return ResponseDTO 包装的情感分析结果
     */
    @PostMapping("/predict")
    public ResponseDTO<SentimentAnalyzeResultDto> predict(@RequestBody SentimentPredictRequestDto requestBody) {
        String stockCode = requestBody != null ? requestBody.getStockCode() : null;
        String news = requestBody != null ? requestBody.getNews() : null;

        log.info("[Sentiment] 情感分析预测 | stockCode={}, newsLength={}", stockCode, news != null ? news.length() : 0);

        if (stockCode == null || stockCode.trim().isEmpty()) {
            return ResponseDTO.error("stockCode 不能为空");
        }
        if (news == null || news.trim().isEmpty()) {
            return ResponseDTO.error("news 不能为空");
        }

        try {
            Map<String, Object> result = trainerService.analyzeSentimentWithDetails(news);
            SentimentAnalyzeResultDto dto = SentimentAnalyzeResultDto.builder()
                    .success(true)
                    .label((String) result.getOrDefault("label", null))
                    .score(castDouble(result.get("score")))
                    .normalizedScore(castDouble(result.get("normalizedScore")))
                    .confidence(castDouble(result.get("confidence")))
                    .probabilities(castProbabilities(result.get("probabilities")))
                    .text((String) result.getOrDefault("text", ""))
                    .modelLoaded((Boolean) result.getOrDefault("modelLoaded", Boolean.FALSE))
                    .stockCode(stockCode)
                    .build();
            return ResponseDTO.success(dto);
        } catch (Exception e) {
            log.error("情感分析预测失败，stockCode={}", stockCode, e);
            return ResponseDTO.error("预测失败：" + e.getMessage());
        }
    }

    /**
     * 针对单只股票的新闻情感分析
     *
     * 根据传入的股票代码和新闻内容，调用情感分析服务对文本进行情感预测，
     * 返回包含情感标签、置信度、情感得分等信息的结果，并回传股票代码，便于前端或策略模块直接关联使用。
     *
     * @param requestBody 请求体，必须包含字段：
     *                    stockCode：股票代码，如 "600519"
     *                    text：新闻内容或公告文本
     * @return 情感分析结果 Map，包含 label / score / confidence / probabilities / text / modelLoaded / stockCode
     */
    @PostMapping("/analyzeByStock")
    public ResponseEntity<SentimentAnalyzeResultDto> analyzeByStock(
            @RequestBody SentimentAnalyzeByStockRequestDto requestBody) {
        String stockCode = requestBody.getStockCode();
        String text = requestBody.getText();

        if (stockCode == null || stockCode.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    SentimentAnalyzeResultDto.builder()
                            .success(false)
                            .message("stockCode 不能为空")
                            .build()
            );
        }
        if (text == null || text.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(
                    SentimentAnalyzeResultDto.builder()
                            .success(false)
                            .message("text 不能为空")
                            .stockCode(stockCode)
                            .build()
            );
        }

        log.info("收到情感分析预测请求 - stockCode={}", stockCode);

        try {
            Map<String, Object> result = trainerService.analyzeSentimentWithDetails(text);
            SentimentAnalyzeResultDto dto = SentimentAnalyzeResultDto.builder()
                    .success(true)
                    .label((String) result.getOrDefault("label", null))
                    .score(castDouble(result.get("score")))
                    .normalizedScore(castDouble(result.get("normalizedScore")))
                    .confidence(castDouble(result.get("confidence")))
                    .probabilities(castProbabilities(result.get("probabilities")))
                    .text((String) result.getOrDefault("text", ""))
                    .modelLoaded((Boolean) result.getOrDefault("modelLoaded", Boolean.FALSE))
                    .stockCode(stockCode)
                    .build();
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            log.error("按股票进行情感分析失败，stockCode={}", stockCode, e);
            return ResponseEntity.internalServerError().body(
                    SentimentAnalyzeResultDto.builder()
                            .success(false)
                            .message("分析失败：" + e.getMessage())
                            .stockCode(stockCode)
                            .build()
            );
        }
    }

    /**
     * 下载预训练模型
     */
    @PostMapping("/download")
    public ResponseEntity<SentimentDownloadDto> downloadModel() {
        log.info("[Sentiment] 下载预训练模型");
        try {
            String modelPath = trainerService.downloadPretrainedModel();

            SentimentDownloadDto dto = SentimentDownloadDto.builder()
                    .success(true)
                    .message("预训练模型下载成功")
                    .modelPath(modelPath)
                    .build();

            return ResponseEntity.ok(dto);

        } catch (Exception e) {
            log.error("下载预训练模型失败", e);
            SentimentDownloadDto dto = SentimentDownloadDto.builder()
                    .success(false)
                    .message("下载失败：" + e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(dto);
        }
    }

}
