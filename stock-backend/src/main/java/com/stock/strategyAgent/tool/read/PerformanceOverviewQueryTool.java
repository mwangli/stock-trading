// AI_GENERATE_START -
package com.stock.strategyAgent.tool.read;

import com.stock.strategyAgent.tool.AgentToolSupport;
import com.stock.tradingExecutor.domain.dto.PerformanceOverviewDto;
import com.stock.tradingExecutor.service.PerformanceReportService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 当前用户账户收益概览只读工具。
 * 已实现收益口径沿用 PerformanceReportService，不包含未卖出持仓浮动盈亏。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
@RequiredArgsConstructor
public class PerformanceOverviewQueryTool {

    private final PerformanceReportService performanceReportService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询累计、当日、当月已实现收益和胜率。
     *
     * @return 收益概览
     */
    @Tool(name = "queryPerformanceOverview", value = "查询累计、当日、当月已实现收益和胜率，不含浮动盈亏")
    public PerformanceOverviewResult query() {
        return toolSupport.execute("queryPerformanceOverview", () -> {
            PerformanceOverviewDto overview = performanceReportService.getOverview();
            return new PerformanceOverviewResult(overview.getCapturedAt(), overview.getTotalAssets(),
                    overview.getAvailableCash(), overview.getMarketValue(), overview.getCumulativeProfit(),
                    overview.getCumulativeReturnRate(), overview.getDailyProfit(), overview.getDailyReturnRate(),
                    overview.getMonthlyProfit(), overview.getMonthlyReturnRate(), overview.getClosedTradeCount(),
                    overview.getWinRate(), LocalDateTime.now());
        });
    }

    /** 收益概览结果。 */
    public record PerformanceOverviewResult(LocalDateTime capturedAt, BigDecimal totalAssets,
                                            BigDecimal availableCash, BigDecimal marketValue,
                                            BigDecimal cumulativeProfit, BigDecimal cumulativeReturnRate,
                                            BigDecimal dailyProfit, BigDecimal dailyReturnRate,
                                            BigDecimal monthlyProfit, BigDecimal monthlyReturnRate,
                                            long closedTradeCount, BigDecimal winRate, LocalDateTime asOf) {
    }
}
// AI_GENERATE_END -
