// AI_GENERATE_START---------
package com.stock.decision.entity;

import org.springframework.data.mongodb.core.index.Indexed;
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
@Document(collection = "backtest_run")
public class BacktestRunEntity {

    /** 数据库主键。 */
    @Id
    private String id;

    /** 回测结果唯一标识。 */
    @Indexed(name = "uk_backtest_run_result", unique = true)
    private String resultId;

    /** 候选选择基准类型。 */
    private String baselineType;

    /** 回测开始日期。 */
    private LocalDate startDate;

    /** 回测结束日期。 */
    private LocalDate endDate;

    /** 运行状态。 */
    private String status;

    /** 是否为降级日线基准。 */
    private boolean degraded;

    /** 行情数据版本。 */
    private String dataVersion;

    /** 股票池版本。 */
    private String stockPoolVersion;

    /** 特征版本。 */
    private String featureVersion;

    /** 费用版本。 */
    private String feeVersion;

    /** 代码版本。 */
    private String codeVersion;

    /** 主资金场景毛收益率。 */
    private BigDecimal totalReturn;

    /** 年化毛收益率。 */
    private BigDecimal annualizedReturn;

    /** 5,000 元场景最大回撤。 */
    private BigDecimal maxDrawdown;

    /** 1,000 元场景净收益率。 */
    private BigDecimal netReturn1000;

    /** 5,000 元场景净收益率。 */
    private BigDecimal netReturn5000;

    /** 10,000 元场景净收益率。 */
    private BigDecimal netReturn10000;

    /** 完成交易数量。 */
    private int totalTrades;

    /** 盈利交易数量。 */
    private int winTrades;

    /** 毛收益胜率。 */
    private BigDecimal winRate;

    /** 盈利交易平均毛收益率。 */
    private BigDecimal avgProfit;

    /** 亏损交易平均毛收益率。 */
    private BigDecimal avgLoss;

    /** T+1 未按计划完成的交易数量。 */
    private int blockedTrades;

    /** 入场跳过或资金不足数量。 */
    private int skippedTrades;

    /** 计算耗时，单位为毫秒。 */
    private long costTimeMs;

    /** 原始请求 JSON。 */
    private String requestJson;

    /** 降级限制 JSON。 */
    private String limitationsJson;

    /** 结果计算完成时间。 */
    private LocalDateTime calculatedAt;

    /** 数据库创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END---------
