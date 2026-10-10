// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商成交只读快照。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerFillSnapshot {

    /** 券商成交编号；协议未提供时允许为空。 */
    private String fillId;

    /** 对应券商委托编号。 */
    private String orderId;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 买卖方向，BUY、SELL 或 UNKNOWN。 */
    private String direction;

    /** 成交价格。 */
    private BigDecimal fillPrice;

    /** 成交数量。 */
    private Integer fillQuantity;

    /** 成交金额。 */
    private BigDecimal fillAmount;

    /** 成交时间。 */
    private LocalDateTime fillTime;
}
// AI_GENERATE_END --
