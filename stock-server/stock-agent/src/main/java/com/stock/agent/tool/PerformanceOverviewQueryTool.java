// AI_GENERATE_START-------
package com.stock.agent.tool;

import com.stock.agent.service.AgentFactQueryService;
import com.stock.agent.model.PerformanceOverviewFact;
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

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询累计、当日、当月已实现收益和胜率。
     *
     * @return 收益概览
     */
    @Tool(name = "queryPerformanceOverview", value = "查询累计、当日、当月已实现收益和胜率，不含浮动盈亏")
    public PerformanceOverviewResult query() {
        return toolSupport.execute("queryPerformanceOverview", () -> {
            PerformanceOverviewFact overview = factQueryService.performanceOverview();
            return new PerformanceOverviewResult(overview.capturedAt(), overview.totalAssets(),
                    overview.availableCash(), overview.marketValue(), overview.cumulativeProfit(),
                    overview.cumulativeReturnRate(), overview.dailyProfit(), overview.dailyReturnRate(),
                    overview.monthlyProfit(), overview.monthlyReturnRate(), overview.closedTradeCount(),
                    overview.winRate(), overview.asOf());
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
// AI_GENERATE_END------