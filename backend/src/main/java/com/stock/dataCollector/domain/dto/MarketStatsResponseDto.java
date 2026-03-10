package com.stock.dataCollector.domain.dto;

import com.stock.dataCollector.domain.vo.MarketStatsDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 市场统计信息响应 DTO
 *
 * 对应 /api/stockInfo/marketStats 接口，包含统计数据与成功标记。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketStatsResponseDto {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 市场统计数据
     */
    private MarketStatsDto data;
}

