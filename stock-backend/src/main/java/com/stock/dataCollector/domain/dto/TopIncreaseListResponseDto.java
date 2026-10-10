package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 涨幅榜列表响应 DTO
 *
 * 封装 /api/stockInfo/listIncreaseRate 接口返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopIncreaseListResponseDto {

    /**
     * 涨幅榜列表
     */
    private List<TopIncreaseItemDto> data;

    /**
     * 是否成功
     */
    private boolean success;
}

