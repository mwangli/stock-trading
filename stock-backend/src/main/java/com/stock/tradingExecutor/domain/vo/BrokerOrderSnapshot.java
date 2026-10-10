// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.vo;

import com.stock.tradingExecutor.domain.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商委托只读快照。
 * 保留标准状态和券商原始状态，避免未知状态被错误归类。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
public class BrokerOrderSnapshot {

    /** 券商委托编号。 */
    private String orderId;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 买卖方向，BUY、SELL 或 UNKNOWN。 */
    private String direction;

    /** 委托价格。 */
    private BigDecimal orderPrice;

    /** 委托数量。 */
    private Integer orderQuantity;

    /** 累计成交数量。 */
    private Integer filledQuantity;

    /** 成交均价。 */
    private BigDecimal averageFillPrice;

    /** 标准委托状态。 */
    private OrderStatus status;

    /** 券商返回的原始状态。 */
    private String rawStatus;

    /** 股东账号脱敏前的协议字段，接口输出时必须脱敏。 */
    private String shareholderAccount;

    /** 市场或交易类别。 */
    private String market;

    /** 委托时间。 */
    private LocalDateTime orderTime;
}
// AI_GENERATE_END --
