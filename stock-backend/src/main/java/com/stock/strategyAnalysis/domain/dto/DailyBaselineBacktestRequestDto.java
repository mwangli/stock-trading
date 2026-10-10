// AI_GENERATE_START --
package com.stock.strategyAnalysis.domain.dto;

import com.stock.strategyAnalysis.domain.entity.BacktestBaselineType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 日线 T+1 固定成交基准请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyBaselineBacktestRequestDto {

    /** 回测开始日期，包含当天。 */
    @NotNull
    private LocalDate startDate;

    /** 回测结束日期，包含当天。 */
    @NotNull
    private LocalDate endDate;

    /** 候选股票选择基准。 */
    @NotNull
    private BacktestBaselineType baselineType;

    /** 可选股票代码；为空时使用当前股票基础表构建受限股票池。 */
    private List<String> stockCodes;

    /** 股票池最大数量，范围为 1 到 1000。 */
    @Min(1)
    @Max(1000)
    private Integer universeLimit;

    /** 动量或反转回看交易日数量，范围为 1 到 60。 */
    @Min(1)
    @Max(60)
    private Integer lookbackDays;

    /** 随机基准种子；为空时使用服务端固定种子。 */
    private Long randomSeed;

    /** 是否明确允许使用当前股票池、未复权日线的降级基准。 */
    private boolean allowDegradedDailyBaseline;
}
// AI_GENERATE_END --
