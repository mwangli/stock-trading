// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商委托只读结果。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerOrderDto {

    /** 券商委托编号。 */
    private String orderId;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 买卖方向。 */
    private String direction;

    /** 委托价格，单位为元。 */
    private BigDecimal orderPrice;

    /** 委托数量。 */
    private Integer orderQuantity;

    /** 累计成交数量。 */
    private Integer filledQuantity;

    /** 成交均价，单位为元。 */
    private BigDecimal averageFillPrice;

    /** 标准委托状态。 */
    private String status;

    /** 券商原始状态。 */
    private String rawStatus;

    /** 脱敏后的股东账号。 */
    private String shareholderAccountMasked;

    /** 市场或交易类别。 */
    private String market;

    /** 委托时间。 */
    private LocalDateTime orderTime;
}
// AI_GENERATE_END --
