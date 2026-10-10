package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 股票数据完整性校验响应 DTO
 *
 * 对应 /api/stock-data/validate 接口。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDataValidationResponseDto {

    private int totalRecords;

    /**
     * 缺失字段统计，例如 missingCode / missingName 等
     */
    private Map<String, Integer> validation;

    private boolean isValid;

    private String dataQuality;
}

