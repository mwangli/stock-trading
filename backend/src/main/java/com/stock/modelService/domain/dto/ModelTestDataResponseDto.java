package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型测试/验证数据响应 DTO
 *
 * 用于 listTestData、listValidateData 接口的返回结构。
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelTestDataResponseDto {

    /**
     * 数据点列表
     */
    private List<ModelTestDataPointDto> points;

    /**
     * 图表 Y 轴最大值
     */
    private Double maxValue;

    /**
     * 图表 Y 轴最小值
     */
    private Double minValue;

    /**
     * 是否成功
     */
    private boolean success;
}
