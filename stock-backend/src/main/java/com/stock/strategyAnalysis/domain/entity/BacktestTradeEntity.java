// AI_GENERATE_START ---
package com.stock.strategyAnalysis.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "backtest_trade")
public class BacktestTradeEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 所属回测结果标识。 */
    @Column(name = "result_id", nullable = false, length = 64)
    private String resultId;

    /** 结果内逐笔序号。 */
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    /** 股票代码。 */
    @Column(name = "stock_code", nullable = false, length = 16)
    private String stockCode;

    /** 信号数据截止日期。 */
    @Column(name = "signal_date", nullable = false)
    private LocalDate signalDate;

    /** 模拟买入日期。 */
    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    /** 计划退出日期。 */
    @Column(name = "planned_exit_date", nullable = false)
    private LocalDate plannedExitDate;

    /** 实际退出日期。 */
    @Column(name = "actual_exit_date")
    private LocalDate actualExitDate;

    /** 原始买入价。 */
    @Column(name = "raw_entry_price", precision = 18, scale = 4)
    private BigDecimal rawEntryPrice;

    /** 原始卖出价。 */
    @Column(name = "raw_exit_price", precision = 18, scale = 4)
    private BigDecimal rawExitPrice;

    /** 计入滑点的买入价。 */
    @Column(name = "executed_entry_price", precision = 18, scale = 4)
    private BigDecimal executedEntryPrice;

    /** 计入滑点的卖出价。 */
    @Column(name = "executed_exit_price", precision = 18, scale = 4)
    private BigDecimal executedExitPrice;

    /** 5,000 元场景成交数量。 */
    @Column(nullable = false)
    private int quantity;

    /** 1,000 元场景成交数量。 */
    @Column(name = "quantity_1000", nullable = false)
    private int quantity1000;

    /** 10,000 元场景成交数量。 */
    @Column(name = "quantity_10000", nullable = false)
    private int quantity10000;

    /** 单笔毛收益率。 */
    @Column(name = "gross_return", nullable = false, precision = 20, scale = 10)
    private BigDecimal grossReturn;

    /** 单笔总费用。 */
    @Column(name = "total_fee", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalFee;

    /** 单笔净盈亏。 */
    @Column(name = "net_profit", nullable = false, precision = 20, scale = 4)
    private BigDecimal netProfit;

    /** 1,000 元场景净盈亏。 */
    @Column(name = "net_profit_1000", nullable = false, precision = 20, scale = 4)
    private BigDecimal netProfit1000;

    /** 10,000 元场景净盈亏。 */
    @Column(name = "net_profit_10000", nullable = false, precision = 20, scale = 4)
    private BigDecimal netProfit10000;

    /** 成交状态。 */
    @Column(nullable = false, length = 32)
    private String status;

    /** 状态原因。 */
    @Column(length = 500)
    private String reason;

    /** 数据库创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
// AI_GENERATE_END ---
