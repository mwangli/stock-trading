// AI_GENERATE_START-----------
package com.stock.strategy.entity;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * T+1 回测逐笔交易持久化实体。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "backtest_trade")
@CompoundIndex(name = "uk_backtest_trade_result_sequence",
        def = "{'resultId': 1, 'sequenceNo': 1}", unique = true)
public class BacktestTradeEntity {

    /** 数据库主键。 */
    @Id
    private String id;

    /** 所属回测结果标识。 */
    private String resultId;

    /** 结果内逐笔序号。 */
    private int sequenceNo;

    /** 股票代码。 */
    private String stockCode;

    /** 信号数据截止日期。 */
    private LocalDate signalDate;

    /** 模拟买入日期。 */
    private LocalDate entryDate;

    /** 计划退出日期。 */
    private LocalDate plannedExitDate;

    /** 实际退出日期。 */
    private LocalDate actualExitDate;

    /** 原始买入价。 */
    private BigDecimal rawEntryPrice;

    /** 原始卖出价。 */
    private BigDecimal rawExitPrice;

    /** 计入滑点的买入价。 */
    private BigDecimal executedEntryPrice;

    /** 计入滑点的卖出价。 */
    private BigDecimal executedExitPrice;

    /** 5,000 元场景成交数量。 */
    private int quantity;

    /** 1,000 元场景成交数量。 */
    private int quantity1000;

    /** 10,000 元场景成交数量。 */
    private int quantity10000;

    /** 单笔毛收益率。 */
    private BigDecimal grossReturn;

    /** 单笔总费用。 */
    private BigDecimal totalFee;

    /** 单笔净盈亏。 */
    private BigDecimal netProfit;

    /** 1,000 元场景净盈亏。 */
    private BigDecimal netProfit1000;

    /** 10,000 元场景净盈亏。 */
    private BigDecimal netProfit10000;

    /** 成交状态。 */
    private String status;

    /** 状态原因。 */
    private String reason;

    /** 数据库创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END-----------
