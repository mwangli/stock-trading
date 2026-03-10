package com.stock.dataCollector.domain.dto;

import com.stock.dataCollector.domain.entity.StockPrice;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 历史价格列表响应 DTO
 *
 * 对应 /api/stockInfo/listHistoryPrices 接口的返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryPriceListResponseDto {

    /**
     * 历史价格列表
     */
    private List<StockPrice> data;

    /**
     * 是否成功
     */
    private boolean success;
}

