package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 股票数据统计响应 DTO
 *
 * 对应 /api/stock-data/statistics 接口的返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockStatisticsResponseDto {

    private long totalCount;

    private Map<String, Long> marketDistribution;

    private Map<String, Long> fieldCompleteness;

    private Map<String, String> completenessRate;
}

