// AI_GENERATE_START --
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
 * T+1 回测运行记录。
 * 保存数据、股票池、特征、费用和代码版本以及汇总指标。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "backtest_run")
public class BacktestRunEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 回测结果唯一标识。 */
    @Column(name = "result_id", nullable = false, unique = true, length = 64)
    private String resultId;

    /** 候选选择基准类型。 */
    @Column(name = "baseline_type", nullable = false, length = 32)
    private String baselineType;

    /** 回测开始日期。 */
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 回测结束日期。 */
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /** 运行状态。 */
    @Column(nullable = false, length = 24)
    private String status;

    /** 是否为降级日线基准。 */
    @Column(nullable = false)
    private boolean degraded;

    /** 行情数据版本。 */
    @Column(name = "data_version", nullable = false)
    private String dataVersion;

    /** 股票池版本。 */
    @Column(name = "stock_pool_version", nullable = false)
    private String stockPoolVersion;

    /** 特征版本。 */
    @Column(name = "feature_version", nullable = false, length = 128)
    private String featureVersion;

    /** 费用版本。 */
    @Column(name = "fee_version", nullable = false, length = 128)
    private String feeVersion;

    /** 代码版本。 */
    @Column(name = "code_version", nullable = false, length = 128)
    private String codeVersion;

    /** 主资金场景毛收益率。 */
    @Column(name = "total_return", nullable = false, precision = 20, scale = 10)
    private BigDecimal totalReturn;

    /** 年化毛收益率。 */
    @Column(name = "annualized_return", nullable = false, precision = 20, scale = 10)
    private BigDecimal annualizedReturn;

    /** 5,000 元场景最大回撤。 */
    @Column(name = "max_drawdown", nullable = false, precision = 20, scale = 10)
    private BigDecimal maxDrawdown;

    /** 1,000 元场景净收益率。 */
    @Column(name = "net_return_1000", nullable = false, precision = 20, scale = 10)
    private BigDecimal netReturn1000;

    /** 5,000 元场景净收益率。 */
    @Column(name = "net_return_5000", nullable = false, precision = 20, scale = 10)
    private BigDecimal netReturn5000;

    /** 10,000 元场景净收益率。 */
    @Column(name = "net_return_10000", nullable = false, precision = 20, scale = 10)
    private BigDecimal netReturn10000;

    /** 完成交易数量。 */
    @Column(name = "total_trades", nullable = false)
    private int totalTrades;

    /** 盈利交易数量。 */
    @Column(name = "win_trades", nullable = false)
    private int winTrades;

    /** 毛收益胜率。 */
    @Column(name = "win_rate", nullable = false, precision = 20, scale = 10)
    private BigDecimal winRate;

    /** 盈利交易平均毛收益率。 */
    @Column(name = "avg_profit", nullable = false, precision = 20, scale = 10)
    private BigDecimal avgProfit;

    /** 亏损交易平均毛收益率。 */
    @Column(name = "avg_loss", nullable = false, precision = 20, scale = 10)
    private BigDecimal avgLoss;

    /** T+1 未按计划完成的交易数量。 */
    @Column(name = "blocked_trades", nullable = false)
    private int blockedTrades;

    /** 入场跳过或资金不足数量。 */
    @Column(name = "skipped_trades", nullable = false)
    private int skippedTrades;

    /** 计算耗时，单位为毫秒。 */
    @Column(name = "cost_time_ms", nullable = false)
    private long costTimeMs;

    /** 原始请求 JSON。 */
    @Column(name = "request_json", nullable = false, columnDefinition = "JSON")
    private String requestJson;

    /** 降级限制 JSON。 */
    @Column(name = "limitations_json", nullable = false, columnDefinition = "JSON")
    private String limitationsJson;

    /** 结果计算完成时间。 */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    /** 数据库创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
// AI_GENERATE_END --
