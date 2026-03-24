package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 模型信息列表单项 DTO (Mock)
 *
 * 对应 /api/modelInfo/list 接口中的模型信息。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoItemDto {

    private String key;

    private String code;

    private String name;

    private String paramsSize;

    private Integer trainTimes;

    private String trainPeriod;

    private Double testDeviation;

    private Double score;

    private LocalDateTime updateTime;

    /**
     * 状态：1=Success 等
     */
    private Integer status;
}

