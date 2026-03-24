package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型测试/验证数据点 DTO
 *
 * 用于图表展示的单个数据点。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTestDataPointDto {

    /**
     * X 轴值，通常为日期字符串
     */
    private String x;

    /**
     * Y 轴值，通常为价格或指标值
     */
    private Double y;

    /**
     * 数据类型，如 "真实值"、"预测值"
     */
    private String type;
}
