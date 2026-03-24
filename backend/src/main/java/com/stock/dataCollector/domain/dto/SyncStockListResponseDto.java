package com.stock.dataCollector.domain.dto;

import com.stock.dataCollector.service.StockDataService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 手动同步股票列表响应 DTO
 *
 * 对应 /api/stock-data/sync 接口返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncStockListResponseDto {

    private boolean success;

    private String message;

    private StockDataService.SyncResult data;
}

