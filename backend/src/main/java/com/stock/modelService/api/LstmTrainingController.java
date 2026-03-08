package com.stock.modelService.api;

import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.modelService.domain.vo.ApiResponse;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.dto.LstmModelListItemDto;
import com.stock.modelService.domain.dto.LstmModelResultDto;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * LSTM 模型训练与列表接口
 *
 * @author AI Assistant
 * @since 1.0
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
    public ApiResponse<PageResult<LstmModelListItemDto>> listModels(
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
        return ApiResponse.success(result);
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
    @GetMapping("/models/{id}/result")
    public ApiResponse<LstmModelResultDto> getModelResult(@PathVariable String id) {
        if (id == null || id.isBlank()) {
            return ApiResponse.error("模型 ID 不能为空");
        }
        Optional<LstmModelDocument> opt = lstmModelRepository.findById(id);
        if (opt.isEmpty()) {
            log.warn("[getModelResult] 未找到模型 id={}", id);
            return ApiResponse.error("模型不存在");
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
        return ApiResponse.success(dto);
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

    @PostMapping("/train")
    public ApiResponse<LstmTrainerService.TrainingResult> trainLstmModel(
            @RequestParam String stockCodes,
            @RequestParam(required = false, defaultValue = "365") int days,
            @RequestParam(required = false) Integer epochs,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(required = false) Double learningRate
    ) {
        LstmTrainerService.TrainingResult result = lstmTrainerService.trainModel(stockCodes, days, epochs, batchSize, learningRate);
        if (result.isSuccess()) {
            return ApiResponse.success(result);
        } else {
            return ApiResponse.error(result.getMessage());
        }
    }
}
