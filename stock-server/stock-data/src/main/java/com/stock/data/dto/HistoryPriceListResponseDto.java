// AI_GENERATE_START---------
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 股票历史价格列表业务数据。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoryPriceListResponseDto {

    /** 按交易日期排列的历史日线列表。 */
    private List<StockPriceItemDto> items;
}
// AI_GENERATE_END---------
