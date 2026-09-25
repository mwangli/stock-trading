// AI_GENERATE_START ---
package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * T+1 基准回测逐笔交易明细。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestTradeDto {

    /** 股票代码。 */
    private String stockCode;

    /** 仅使用该日期及之前数据生成信号的日期。 */
    private LocalDate signalDate;

    /** T 日实际模拟买入日期。 */
    private LocalDate entryDate;

    /** 计划的 T+1 退出日期。 */
    private LocalDate plannedExitDate;

    /** 实际找到可退出价格的日期。 */
    private LocalDate actualExitDate;

    /** 未计滑点的 T 日开盘价。 */
    private BigDecimal rawEntryPrice;

    /** 未计滑点的退出日开盘价。 */
    private BigDecimal rawExitPrice;

    /** 计入单边滑点后的买入价。 */
    private BigDecimal executedEntryPrice;

    /** 计入单边滑点后的卖出价。 */
    private BigDecimal executedExitPrice;

    /** 5,000 元主资金场景下的买入数量。 */
    private int quantity;

    /** 1,000 元资金场景下的买入数量。 */
    private int quantity1000;

    /** 10,000 元资金场景下的买入数量。 */
    private int quantity10000;

    /** 不计费用和滑点的单笔毛收益率。 */
    private BigDecimal grossReturn;

    /** 主资金场景下的佣金、印花税和其他费用合计。 */
    private BigDecimal totalFee;

    /** 主资金场景下的净盈亏金额。 */
    private BigDecimal netProfit;

    /** 1,000 元资金场景下的净盈亏金额。 */
    private BigDecimal netProfit1000;

    /** 10,000 元资金场景下的净盈亏金额。 */
    private BigDecimal netProfit10000;

    /** 成交状态，例如 FILLED、ENTRY_LIMIT_UP、EXIT_BLOCKED。 */
    private String status;

    /** 未成交或延迟退出原因。 */
    private String reason;
}
// AI_GENERATE_END ---
