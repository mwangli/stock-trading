// AI_GENERATE_START----
package com.stock.trading.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 历史订单 FIFO 汇总交易记录。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
public class TradeRecordDto {

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 交易状态：OPEN、PARTIAL 或 CLOSED。 */
    private String status;

    /** 买入委托编号。 */
    private String buyOrderNo;

    /** 已匹配卖出委托编号。 */
    private String sellOrderNos;

    /** 买入成交时间。 */
    private LocalDateTime buyTime;

    /** 首次卖出成交时间。 */
    private LocalDateTime firstSellTime;

    /** 最后一次卖出成交时间。 */
    private LocalDateTime lastSellTime;

    /** 买入数量。 */
    private Integer buyQuantity;

    /** 已卖出数量。 */
    private Integer soldQuantity;

    /** 剩余数量。 */
    private Integer remainingQuantity;

    /** 买入成交均价。 */
    private BigDecimal buyPrice;

    /** 卖出加权均价。 */
    private BigDecimal averageSellPrice;

    /** 买入总金额，不含费用。 */
    private BigDecimal buyAmount;

    /** 累计卖出金额，不含费用。 */
    private BigDecimal sellAmount;

    /** 已卖出部分对应的买卖双边手续费合计。 */
    private BigDecimal totalFee;

    /** 毛收益金额。 */
    private BigDecimal grossProfit;

    /** 净收益金额。 */
    private BigDecimal netProfit;

    /** 毛收益率，小数形式。 */
    private BigDecimal grossReturnRate;

    /** 净收益率，小数形式。 */
    private BigDecimal returnRate;

    /** 数量加权持仓自然日数。 */
    private BigDecimal holdingDays;

    /** 复合日净收益率，小数形式。 */
    private BigDecimal dailyReturnRate;

    /** 按毛收益率和持仓天数折算的复合日毛收益率，小数形式。 */
    private BigDecimal grossDailyReturnRate;
}
// AI_GENERATE_END----
