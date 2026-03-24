package com.stock.modelService.api;

import com.stock.modelService.domain.dto.ModelInfoChartDataResponseDto;
import com.stock.modelService.domain.dto.ModelInfoChartDataWrapperDto;
import com.stock.modelService.domain.dto.ModelInfoItemDto;
import com.stock.modelService.domain.dto.ModelInfoListResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 模型信息控制器
 * <p>
 * 对应前端 /api/modelInfo/* 接口，提供模型列表、测试/验证图表数据等。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Slf4j
@RestController
@RequestMapping("/api/modelInfo")
public class ModelInfoController {

    /**
     * 获取模型列表
     *
     * @param current  当前页
     * @param pageSize 每页条数
     * @param name     模型名称筛选
     * @param code     股票代码筛选
     * @return 模型列表分页结果
     */
    @GetMapping("/list")
    public ResponseEntity<ModelInfoListResponseDto> listModels(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code) {

        log.info("[ModelInfo] 分页查询模型列表 | current={}, pageSize={}, name={}, code={}", current, pageSize, name, code);

        List<ModelInfoItemDto> models = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String stockCode = i % 2 == 0 ? "600519" : "000858";
            ModelInfoItemDto model = ModelInfoItemDto.builder()
                    .key("m" + i)
                    .code(stockCode)
                    .name(i % 2 == 0 ? "贵州茅台" : "五粮液")
                    .paramsSize("100k")
                    .trainTimes(10 + i)
                    .trainPeriod("2h")
                    .testDeviation(0.0123 + i * 0.001)
                    .score(95.5 - i)
                    .updateTime(LocalDateTime.now().minusDays(i))
                    .status(1)
                    .build();
            models.add(model);
        }

        ModelInfoListResponseDto response = ModelInfoListResponseDto.builder()
                .data(models)
                .total(models.size())
                .success(true)
                .current(current)
                .pageSize(pageSize)
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 获取测试数据（模型预测 vs 真实值图表）
     *
     * @param code 股票代码
     * @return 图表数据，包含 points、maxValue、minValue
     */
    @GetMapping("/listTestData")
    public ResponseEntity<ModelInfoChartDataResponseDto> listTestData(@RequestParam String code) {
        log.info("[ModelInfo] 获取测试数据 | code={}", code);
        ModelInfoChartDataWrapperDto data = generateMockChartData();
        ModelInfoChartDataResponseDto response = ModelInfoChartDataResponseDto.builder()
                .success(true)
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * 获取验证数据（模型预测 vs 真实值图表）
     *
     * @param code 股票代码
     * @return 图表数据，包含 points、maxValue、minValue
     */
    @GetMapping("/listValidateData")
    public ResponseEntity<ModelInfoChartDataResponseDto> listValidateData(@RequestParam String code) {
        log.info("[ModelInfo] 获取验证数据 | code={}", code);
        ModelInfoChartDataWrapperDto data = generateMockChartData();
        ModelInfoChartDataResponseDto response = ModelInfoChartDataResponseDto.builder()
                .success(true)
                .data(data)
                .build();
        return ResponseEntity.ok(response);
    }

    private ModelInfoChartDataWrapperDto generateMockChartData() {
        List<ModelInfoChartDataResponseDto.ChartPointDto> points = new ArrayList<>();
        Random random = new Random();
        double price = 100.0;
        double max = 0;
        double min = 10000;

        for (int i = 0; i < 50; i++) {
            double change = (random.nextDouble() - 0.5) * 5;
            price += change;

            points.add(ModelInfoChartDataResponseDto.ChartPointDto.builder()
                    .x(LocalDate.now().minusDays(50 - i).toString())
                    .y(price)
                    .type("真实值")
                    .build());

            points.add(ModelInfoChartDataResponseDto.ChartPointDto.builder()
                    .x(LocalDate.now().minusDays(50 - i).toString())
                    .y(price + (random.nextDouble() - 0.5) * 2)
                    .type("预测值")
                    .build());

            max = Math.max(max, price);
            min = Math.min(min, price);
        }

        return ModelInfoChartDataWrapperDto.builder()
                .points(points)
                .maxValue(max + 10)
                .minValue(min - 10)
                .build();
    }
}
