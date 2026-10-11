// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日交易收益统计快照。
 * 所有收益指标均由已持久化的 FIFO 交易记录汇总生成。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Entity
@Accessors(chain = true)
@Table(name = "trade_performance_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uk_trade_performance_account_date", columnNames = {"broker_code", "account_id", "as_of_date"}))
public class TradePerformanceSnapshotEntity {

    /** 数据库主键。 */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** 券商编码。 */
    @Column(name = "broker_code", nullable = false, length = 32)
    private String brokerCode;
    /** 账户哈希标识。 */
    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;
    /** 统计所属日期。 */
    @Column(name = "as_of_date", nullable = false)
    private LocalDate asOfDate;
    /** 累计已实现净收益，单位为元。 */
    @Column(name = "cumulative_profit", nullable = false, precision = 20, scale = 4)
    private BigDecimal cumulativeProfit;
    /** 累计已实现收益率，小数形式。 */
    @Column(name = "cumulative_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal cumulativeReturnRate;
    /** 当日已实现净收益，单位为元。 */
    @Column(name = "daily_profit", nullable = false, precision = 20, scale = 4)
    private BigDecimal dailyProfit;
    /** 当日已实现收益率，小数形式。 */
    @Column(name = "daily_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal dailyReturnRate;
    /** 当月已实现净收益，单位为元。 */
    @Column(name = "monthly_profit", nullable = false, precision = 20, scale = 4)
    private BigDecimal monthlyProfit;
    /** 当月已实现收益率，小数形式。 */
    @Column(name = "monthly_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal monthlyReturnRate;
    /** 交易记录总数，包含未完成记录。 */
    @Column(name = "total_trade_count", nullable = false)
    private Long totalTradeCount;
    /** 已完成交易记录数。 */
    @Column(name = "closed_trade_count", nullable = false)
    private Long closedTradeCount;
    /** 盈利交易记录数。 */
    @Column(name = "winning_trade_count", nullable = false)
    private Long winningTradeCount;
    /** 亏损交易记录数。 */
    @Column(name = "losing_trade_count", nullable = false)
    private Long losingTradeCount;
    /** 已完成交易胜率，小数形式。 */
    @Column(name = "win_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal winRate;
    /** 已完成交易平均收益率，小数形式。 */
    @Column(name = "average_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal averageReturnRate;
    /** 单笔最高收益率，小数形式。 */
    @Column(name = "best_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal bestReturnRate;
    /** 单笔最低收益率，小数形式。 */
    @Column(name = "worst_return_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal worstReturnRate;
    /** 按收益率排序的交易排行 JSON。 */
    @Lob
    @Column(name = "ranking_json", columnDefinition = "LONGTEXT")
    private String rankingJson;

    /** 日收益明细 JSON。 */
    @Lob
    @Column(name = "daily_returns_json", columnDefinition = "LONGTEXT")
    private String dailyReturnsJson;

    /** 月收益明细 JSON。 */
    @Lob
    @Column(name = "monthly_returns_json", columnDefinition = "LONGTEXT")
    private String monthlyReturnsJson;

    /** 快照生成时间。 */
    @Column(name = "generated_at", nullable = false)
    private LocalDateTime generatedAt;

    /** 初始化创建时间。 */
    @PrePersist
    protected void onCreate() { generatedAt = generatedAt != null ? generatedAt : LocalDateTime.now(); }
    /** 刷新快照生成时间。 */
    @PreUpdate
    protected void onUpdate() { generatedAt = LocalDateTime.now(); }
}
// AI_GENERATE_END --