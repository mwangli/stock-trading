package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 模型列表响应 DTO
 *
 * 对应 /api/modelInfo/list 接口返回。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfoListResponseDto {

    private List<ModelInfoItemDto> data;

    private long total;

    private boolean success;

    private int current;

    private int pageSize;
}

