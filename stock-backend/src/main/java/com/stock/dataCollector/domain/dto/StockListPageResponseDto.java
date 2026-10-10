package com.stock.dataCollector.domain.dto;

import com.stock.dataCollector.domain.entity.StockInfo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * StockDataController 中分页股票列表响应 DTO
 *
 * 对应 /api/stock-data/list 接口。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockListPageResponseDto {

    private int total;

    private int page;

    private int size;

    private List<StockInfo> data;
}

