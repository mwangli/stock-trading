// AI_GENERATE_START ---
package com.stock.tradingExecutor.domain.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 已成交历史订单和 FIFO 汇总交易记录分页查询条件。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
public class BrokerTransactionQueryRequest {

    /** 股票代码或股票名称关键字；历史订单查询还可匹配委托编号。 */
    private String keyword;

    /** 历史订单买卖方向，支持 BUY、SELL 或空值；交易记录查询忽略该字段。 */
    private String direction;

    /** 查询开始日期，包含当天。 */
    private LocalDate startDate;

    /** 查询结束日期，包含当天。 */
    private LocalDate endDate;

    /** 当前页码，从 1 开始。 */
    private Integer current = 1;

    /** 每页记录数，范围 1 至 100。 */
    private Integer pageSize = 20;
}
// AI_GENERATE_END ---
