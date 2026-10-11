// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 已成交历史订单分页响应。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
public class BrokerOrderPageResponseDto {

    /** 当前页委托明细。 */
    private List<BrokerOrderDto> items;

    /** 符合查询条件的委托总数。 */
    private long total;

    /** 当前页码，从 1 开始。 */
    private int current;

    /** 每页记录数。 */
    private int pageSize;
}
// AI_GENERATE_END --
