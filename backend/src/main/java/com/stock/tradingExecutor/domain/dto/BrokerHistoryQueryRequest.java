// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 券商历史委托或成交查询条件。
 * 单次查询范围不得超过 31 个自然日。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
public class BrokerHistoryQueryRequest {

    /** 查询开始日期，包含当天。 */
    private LocalDate startDate;

    /** 查询结束日期，包含当天。 */
    private LocalDate endDate;
}
// AI_GENERATE_END --
