package com.stock.modelService.api;

import com.stock.modelService.domain.dto.ModelTrainingRecordDto;
import com.stock.modelService.domain.dto.PageResult;
import com.stock.modelService.domain.vo.ApiResponse;
import com.stock.modelService.service.ModelTrainingRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型训练记录控制器
 * <p>
 * 为前端“量化模型”菜单提供分页查询接口，返回每只股票的
 * LSTM 模型训练状态（已训练/未训练）、最近训练时间、训练耗时等信息。
 * </p>
 *
 * <p>接口路径：{@code GET /api/models/training-records}</p>
 *
 * @author AI Assistant
 * @since 1.0
 */
@Slf4j
@RestController
@RequestMapping("/api/models/training-records")
@RequiredArgsConstructor
public class ModelTrainingRecordController {

    private final ModelTrainingRecordService modelTrainingRecordService;

    /**
     * 分页查询模型训练记录
     *
     * @param keyword  可选，按股票代码或名称模糊搜索
     * @param current  当前页，从 1 开始
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResult<ModelTrainingRecordDto>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") int current,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        log.info("[ModelTrainingRecordController#page] 接收请求 | keyword={}, current={}, pageSize={}", keyword, current, pageSize);
        PageResult<ModelTrainingRecordDto> result = modelTrainingRecordService.page(keyword, current, pageSize);
        return ApiResponse.success(result);
    }
}

