// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 券商成交列表响应。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerFillListResponseDto {

    /** 成交明细。 */
    private List<BrokerFillDto> items;

    /** 成交条数。 */
    private int total;

    /** 查询开始日期；当日查询时为空。 */
    private LocalDate startDate;

    /** 查询结束日期；当日查询时为空。 */
    private LocalDate endDate;
}
// AI_GENERATE_END --
