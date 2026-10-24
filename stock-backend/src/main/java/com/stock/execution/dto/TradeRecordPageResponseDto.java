// AI_GENERATE_START----
package com.stock.execution.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * FIFO 汇总交易记录分页响应。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
public class TradeRecordPageResponseDto {

    /** 当前页交易记录。 */
    private List<TradeRecordDto> items;

    /** 符合条件的交易记录总数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页记录数。 */
    private int pageSize;
}
// AI_GENERATE_END----
