// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商成交只读结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerFillDto {

    /** 券商成交编号；券商未返回时为空。 */
    private String fillId;

    /** 对应券商委托编号。 */
    private String orderId;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 买卖方向。 */
    private String direction;

    /** 成交价格，单位为元。 */
    private BigDecimal fillPrice;

    /** 成交数量。 */
    private Integer fillQuantity;

    /** 成交金额，单位为元。 */
    private BigDecimal fillAmount;

    /** 成交时间。 */
    private LocalDateTime fillTime;
}
// AI_GENERATE_END --
