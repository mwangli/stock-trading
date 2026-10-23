// AI_GENERATE_START------
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 股票列表分页业务数据。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockInfoListResponseDto {

    /** 当前页股票列表。 */
    private List<StockInfoItemDto> items;

    /** 符合条件的总记录数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页记录数。 */
    private int pageSize;
}
// AI_GENERATE_END------
