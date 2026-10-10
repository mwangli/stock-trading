// AI_GENERATE_START ---
package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * T+1 回测数据可用性审计结果。
 * 用于区分可运行的日线基准与达到生产回测标准所需的完整数据能力。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestDataAuditDto {

    /** 审计请求的开始日期。 */
    private LocalDate requestedStartDate;

    /** 审计请求的结束日期。 */
    private LocalDate requestedEndDate;

    /** 当前 MySQL 股票基础信息数量。 */
    private long currentStockCount;

    /** 请求区间内 MongoDB 日线记录数量。 */
    private long dailyRecordCount;

    /** 请求区间内具有日线数据的股票数量。 */
    private int dailyStockCount;

    /** 请求区间内实际最早日线日期。 */
    private LocalDate earliestDailyDate;

    /** 请求区间内实际最晚日线日期。 */
    private LocalDate latestDailyDate;

    /** 预期的 5 分钟行情集合是否存在。 */
    private boolean minuteCollectionExists;

    /** 5 分钟行情集合记录数量。 */
    private long minuteRecordCount;

    /** 请求区间内具有 5 分钟行情的股票数量。 */
    private int minuteStockCount;

    /** 请求区间内实际最早 5 分钟行情日期。 */
    private LocalDate earliestMinuteDate;

    /** 请求区间内实际最晚 5 分钟行情日期。 */
    private LocalDate latestMinuteDate;

    /** 5 分钟行情声明的成交量单位。 */
    private List<String> minuteVolumeUnits;

    /** 5 分钟行情声明的成交额单位。 */
    private List<String> minuteAmountUnits;

    /** 与请求区间相交的历史股票池版本数量。 */
    private long historicalUniverseRecordCount;

    /** 请求开始日期有效的历史股票数量。 */
    private long historicalUniverseStartCount;

    /** 请求结束日期有效的历史股票数量。 */
    private long historicalUniverseEndCount;

    /** 历史股票池最早生效日期。 */
    private LocalDate earliestHistoricalUniverseDate;

    /** 请求区间内去重后的交易日历自然日期数量。 */
    private long tradeCalendarRecordCount;

    /** 交易日历最早日期。 */
    private LocalDate earliestTradeCalendarDate;

    /** 交易日历最晚日期。 */
    private LocalDate latestTradeCalendarDate;

    /** 请求区间内复权因子数量。 */
    private long adjustmentFactorRecordCount;

    /** 请求区间内具有复权因子的股票数量。 */
    private int adjustmentFactorStockCount;

    /** 请求区间内实际最早复权因子日期。 */
    private LocalDate earliestAdjustmentFactorDate;

    /** 请求区间内实际最晚复权因子日期。 */
    private LocalDate latestAdjustmentFactorDate;

    /** 日线是否包含可验证的复权因子或复权类型。 */
    private boolean adjustedPriceAvailable;

    /** 是否存在带生效日期的历史股票池。 */
    private boolean historicalUniverseAvailable;

    /** 是否存在独立交易日历。 */
    private boolean tradeCalendarAvailable;

    /** 成交量单位是否已经通过数据源文档或真实样本确认。 */
    private boolean volumeUnitVerified;

    /** 当前数据是否足以运行降级版日线固定基准。 */
    private boolean dailyBaselineRunnable;

    /** 当前数据是否达到生产级五年 T+1 回测门禁。 */
    private boolean productionBacktestReady;

    /** 阻止生产级回测的具体原因。 */
    private List<String> blockers;

    /** 审计执行时间。 */
    private LocalDateTime auditedAt;
}
// AI_GENERATE_END ---
