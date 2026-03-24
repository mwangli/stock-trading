package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型信息图表数据响应 DTO
 * <p>
 * 用于 listTestData、listValidateData 等接口，替代 Map 返回值。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoChartDataResponseDto {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 图表数据，包含 points、maxValue、minValue
     */
    private ModelInfoChartDataWrapperDto data;

    /**
     * 图表数据点
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartPointDto {
        /** 横轴值，如日期 */
        private String x;
        /** 纵轴值，如价格 */
        private Double y;
        /** 类型，如 "真实值"、"预测值" */
        private String type;
    }
}
