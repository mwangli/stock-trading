package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型信息图表数据包装 DTO
 * <p>
 * 包含 points、maxValue、minValue，用于 listTestData、listValidateData 的 data 字段。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoChartDataWrapperDto {

    /**
     * 数据点列表，每项包含 x(日期)、y(数值)、type(类型)
     */
    private List<ModelInfoChartDataResponseDto.ChartPointDto> points;

    /**
     * 纵轴最大值
     */
    private double maxValue;

    /**
     * 纵轴最小值
     */
    private double minValue;
}
