// AI_GENERATE_START --
package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * T+1 回测结果 DTO。
 * 同时保留历史字段并补充数据、费用和逐笔追溯信息。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDto {

    /** 回测结果唯一标识。 */
    private String resultId;

    /** 回测开始日期。 */
    private LocalDate startDate;

    /** 回测结束日期。 */
    private LocalDate endDate;

    /** 主资金场景的不计费用毛收益率。 */
    private double totalReturn;

    /** 主资金场景毛收益折算年化收益率。 */
    private double annualizedReturn;

    /** 5,000 元净值曲线最大回撤。 */
    private double maxDrawdown;

    /** 主资金场景实际完成交易数量。 */
    private int totalTrades;

    /** 毛收益大于零的交易数量。 */
    private int winTrades;

    /** 毛收益胜率。 */
    private double winRate;

    /** 盈利交易平均毛收益率。 */
    private double avgProfit;

    /** 亏损交易平均毛收益率。 */
    private double avgLoss;

    /** 兼容历史字段，日线基准不计算盘中高点捕获率。 */
    private double avgHighCaptureRate;

    /** 结果计算完成时间。 */
    private String calculateTime;

    /** 计算耗时，单位为毫秒。 */
    private long costTimeMs;

    /** 基准类型。 */
    private String baselineType;

    /** 数据版本说明。 */
    private String dataVersion;

    /** 股票池版本说明。 */
    private String stockPoolVersion;

    /** 特征版本。 */
    private String featureVersion;

    /** 费用版本。 */
    private String feeVersion;

    /** 代码版本。 */
    private String codeVersion;

    /** 1,000 元资金场景净收益率。 */
    private double netReturn1000;

    /** 5,000 元资金场景净收益率。 */
    private double netReturn5000;

    /** 10,000 元资金场景净收益率。 */
    private double netReturn10000;

    /** 因涨跌停、停牌或缺失数据无法完成的交易数量。 */
    private int blockedTrades;

    /** 因资金不足或入场不可成交跳过的交易数量。 */
    private int skippedTrades;

    /** 是否为明确允许的降级日线基准。 */
    private boolean degraded;

    /** 数据与执行口径限制说明。 */
    private List<String> limitations;

    /** 逐笔交易明细。 */
    private List<BacktestTradeDto> trades;
}
// AI_GENERATE_END --
