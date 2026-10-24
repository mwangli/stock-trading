// AI_GENERATE_START------
package com.stock.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 涨幅榜业务数据。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopIncreaseListResponseDto {

    /** 按涨跌幅降序排列的股票列表。 */
    private List<TopIncreaseItemDto> items;
}
// AI_GENERATE_END------
