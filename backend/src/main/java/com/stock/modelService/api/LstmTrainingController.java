package com.stock.modelService.api;

import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.dto.LstmModelListItemDto;
import com.stock.modelService.domain.dto.LstmModelResultDto;
import com.stock.modelService.domain.dto.LstmPredictionResultDto;
import com.stock.modelService.domain.dto.PageResult;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.service.LstmTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * LSTM 模型训练与列表接口
 *
 * 提供模型列表分页查询、模型结果查询、训练触发、价格预测等功能。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/lstm")
public class LstmTrainingController {
    private final LstmTrainerService lstmTrainerService;
    private final LstmModelRepository lstmModelRepository;
    private final StockInfoRepository stockInfoRepository;

    /**
     * 分页查询 LSTM 模型列表
     *
     * @param keyword  可选，按股票代码/模型名称模糊筛选
     * @param current  当前页，从 1 开始
     * @param pageSize 每页条数
     * @param sortBy   排序字段：createdAt | epoch
     * @param sortOrder 排序方向：asc | desc
     */
    @GetMapping("/models")
    public ResponseDTO<PageResult<LstmModelListItemDto>> listModels(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") int current,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder
    ) {
        log.info("[listModels] 接口被调用 | keyword={}, current={}, pageSize={}, sortBy={}, sortOrder={}",
                keyword, current, pageSize, sortBy, sortOrder);

        if (current < 1) current = 1;
        if (pageSize < 1) pageSize = 10;
        if (pageSize > 100) pageSize = 100;
        String safeSortBy = "valLoss".equalsIgnoreCase(sortBy) ? "valLoss" : "epoch".equalsIgnoreCase(sortBy) ? "epoch" : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, safeSortBy);
        Pageable pageable = PageRequest.of(current - 1, pageSize, sort);

        Page<LstmModelDocument> page;
        if (StringUtils.hasText(keyword)) {
            page = lstmModelRepository.findByModelNameRegexForList(keyword.trim(), pageable);
            log.info("[listModels] 已按 keyword 查询 MongoDB(排除大字段) | totalElements={}, contentSize={}",
                    page.getTotalElements(), page.getContent().size());
        } else {
            page = lstmModelRepository.findAllForList(pageable);
            log.info("[listModels] 已执行 findAllForList 分页查询(排除大字段) | totalElements={}, contentSize={}",
                    page.getTotalElements(), page.getContent().size());
        }

        List<LstmModelDocument> content = page.getContent();
        // 批量解析首只股票代码，一次查询 StockInfo，避免 N+1
        List<String> codes = content.stream()
                .map(doc -> extractFirstCode(doc.getModelName()))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<String, String> codeToName = new HashMap<>();
        if (!codes.isEmpty()) {
            stockInfoRepository.findByCodeIn(codes)
                    .forEach(si -> codeToName.put(si.getCode(), si.getName()));
        }
        List<LstmModelListItemDto> list = content.stream()
                .map(doc -> toListItem(doc, codeToName))
                .collect(Collectors.toList());
        PageResult<LstmModelListItemDto> result = PageResult.<LstmModelListItemDto>builder()
                .list(list)
                .total(page.getTotalElements())
                .build();
        log.info("[listModels] 即将返回 | list.size()={}, total={}", list.size(), result.getTotal());
        return ResponseDTO.success(result);
    }

    /**
     * 从 modelName（可能为逗号分隔的多只股票代码）中解析出第一个股票代码
     */
    private String extractFirstCode(String modelName) {
        if (modelName == null || modelName.isEmpty()) return null;
        return java.util.Arrays.stream(modelName.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /**
     * 查询指定模型的结果数据：收益金额与分数（不返回损失）
     *
     * @param id 模型文档 ID（与列表接口返回的 id 一致）
     * @return 收益金额、分数；模型不存在时返回错误
     */
    @GetMapping("/models/result/{id}")
    public ResponseDTO<LstmModelResultDto> getModelResult(@PathVariable String id) {
        log.info("查询模型结果: id={}", id);
        if (id == null || id.isBlank()) {
            return ResponseDTO.error("模型 ID 不能为空");
        }
        Optional<LstmModelDocument> opt = lstmModelRepository.findById(id);
        if (opt.isEmpty()) {
            log.warn("[getModelResult] 未找到模型 id={}", id);
            return ResponseDTO.error("模型不存在");
        }
        LstmModelDocument doc = opt.get();
        Double score = null;
        if (doc.getValLoss() != null) {
            double v = doc.getValLoss();
            score = Math.max(0, Math.min(100, (1.0 - v) * 100));
        }
        LstmModelResultDto dto = LstmModelResultDto.builder()
                .modelId(doc.getId())
                .modelName(doc.getModelName())
                .profitAmount(null)
                .score(score)
                .build();
        return ResponseDTO.success(dto);
    }

    private LstmModelListItemDto toListItem(LstmModelDocument doc, Map<String, String> codeToName) {
        String firstCode = extractFirstCode(doc.getModelName());
        String name = firstCode != null
                ? codeToName.getOrDefault(firstCode, doc.getModelName())
                : doc.getModelName();
        Double score = null;
        if (doc.getValLoss() != null) {
            double v = doc.getValLoss();
            score = Math.max(0, Math.min(100, (1.0 - v) * 100));
        }
        return LstmModelListItemDto.builder()
                .id(doc.getId())
                .modelName(doc.getModelName())
                .name(name != null ? name : doc.getModelName())
                .epoch(doc.getEpoch())
                .createdAt(doc.getCreatedAt())
                .modelVersion(doc.getModelVersion())
                .trainLoss(doc.getTrainLoss())
                .valLoss(doc.getValLoss())
                .profitAmount(null)
                .score(score)
                .build();
    }

    /**
     * 触发 LSTM 模型训练（异步 + 增量迭代）
     * 立即返回，训练在后台异步执行
     *
     * @param stockCodes  股票代码，逗号分隔
     * @param days        训练数据天数
     * @param epochs      训练轮数
     * @param batchSize   批次大小
     * @param learningRate 学习率
     * @param forceFull   是否强制全量训练（跳过增量训练）
     * @return 训练结果
     */
    @PostMapping("/train")
    public ResponseDTO<LstmTrainerService.TrainingResult> trainLstmModel(
            @RequestParam String stockCodes,
            @RequestParam(required = false, defaultValue = "365") int days,
            @RequestParam(required = false) Integer epochs,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(required = false) Double learningRate,
            @RequestParam(required = false, defaultValue = "false") boolean forceFull
    ) {
        log.info("提交 LSTM 训练任务: stockCodes={}, days={}, epochs={}, forceFull={}",
                stockCodes, days, epochs, forceFull);

        if (!lstmTrainerService.tryAcquireTrainingLock(stockCodes)) {
            log.warn("股票 {} 正在训练中，拒绝重复训练请求", stockCodes);
            return ResponseDTO.error("该股票正在训练中，请稍后再试");
        }

        lstmTrainerService.markTrainingSync(stockCodes, true);

        String finalStockCodes = stockCodes;
        CompletableFuture.runAsync(() -> {
            try {
                String trainingType = forceFull ? "full" : "incremental";
                LstmTrainerService.TrainingResult result = lstmTrainerService.trainIncremental(
                        finalStockCodes, days, epochs, batchSize, learningRate, forceFull, trainingType);
                log.info("异步训练完成: success={}, incremental={}, message={}",
                        result.isSuccess(), result.isIncremental(), result.getMessage());
            } catch (Exception e) {
                log.error("异步训练异常: stockCodes={}", finalStockCodes, e);
            } finally {
                lstmTrainerService.releaseTrainingLock(finalStockCodes);
            }
        });

        return ResponseDTO.success(
            LstmTrainerService.TrainingResult.builder()
                .success(true)
                .message("训练任务已提交")
                .incremental(forceFull ? false : true)
                .build()
        );
    }

    /**
     * 重新训练 LSTM 模型
     *
     * <p>
     * 用于在已有模型基础上再次发起训练任务，通常在模型效果衰减或行情环境发生变化时调用。
     * 入参与 {@link #trainLstmModel(String, int, Integer, Integer, Double)} 一致，
     * 仅在日志与语义上区分为“重新训练”。
     * </p>
     *
     * @param stockCodes  股票代码，逗号分隔，例如 "600519,000858"
     * @param days        训练所使用的历史天数
     * @param epochs      训练轮数，可为空则使用默认配置
     * @param batchSize   批次大小，可为空则使用默认配置
     * @param learningRate 学习率，可为空则使用默认配置
     * @return 重新训练结果，包含损失、样本数等统计信息
     */
    @PostMapping("/retrain")
    public ResponseDTO<LstmTrainerService.TrainingResult> retrainLstmModel(
            @RequestParam String stockCodes,
            @RequestParam(required = false, defaultValue = "365") int days,
            @RequestParam(required = false) Integer epochs,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(required = false) Double learningRate
    ) {
        log.info("重新训练 LSTM 模型: stockCodes={}, days={}, epochs={}", stockCodes, days, epochs);
        LstmTrainerService.TrainingResult result = lstmTrainerService.trainModel(stockCodes, days, epochs, batchSize, learningRate, "full");
        if (result.isSuccess()) {
            return ResponseDTO.success(result);
        } else {
            return ResponseDTO.error(result.getMessage());
        }
    }

    /**
     * 使用最新的 LSTM 模型对单只股票进行下一交易日价格预测
     *
     * @param stockCode 股票代码，例如 "600519"
     * @return 预测结果 DTO，包含预测收盘价、最新收盘价与预测涨跌幅
     */
    @GetMapping("/predict")
    public ResponseDTO<LstmPredictionResultDto> predictNext(@RequestParam String stockCode) {
        log.info("[predictNext] 接口被调用 | stockCode={}", stockCode);
        try {
            LstmPredictionResultDto result = lstmTrainerService.predictNext(stockCode);
            return ResponseDTO.success(result);
        } catch (Exception e) {
            log.error("[predictNext] 预测失败 | stockCode={}", stockCode, e);
            return ResponseDTO.error("预测失败：" + e.getMessage());
        }
    }
}
