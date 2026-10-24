// AI_GENERATE_START -
package com.stock.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 跨业务消费的股票单日可成交性事实。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTradabilityDto {
    /** 股票代码。 */
    private String stockCode;
    /** 交易日期。 */
    private LocalDate tradeDate;
    /** 开盘买入状态。 */
    private String openBuyStatus;
    /** 开盘卖出状态。 */
    private String openSellStatus;
    /** 买入限制原因，可为空。 */
    private String buyReason;
    /** 卖出限制原因，可为空。 */
    private String sellReason;
    /** 事实采集时间。 */
    private LocalDateTime capturedAt;
}
// AI_GENERATE_END -
