// AI_GENERATE_START -----
package com.stock.agent.tool;

import com.stock.agent.model.PerformanceAnalysisFact;
import com.stock.agent.service.AgentFactQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * 指定自然月收益和亏损原因只读工具。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
@RequiredArgsConstructor
public class PerformanceAnalysisQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询指定自然月的已实现收益、费用和主要损益交易。
     *
     * @param request 月份请求；为空时查询上一个完整自然月
     * @return 月度收益分析事实
     */
    @Tool(name = "queryPerformanceAnalysis", value = "查询月度已实现收益、费用、胜率及主要盈亏交易。period 可选 CURRENT_MONTH、LAST_COMPLETE_MONTH、SPECIFIC_MONTH、LATEST_MONTH_WITH_TRADES；仅 SPECIFIC_MONTH 需要 yearMonth=yyyy-MM")
    public PerformanceAnalysisFact query(PerformanceAnalysisRequest request) {
        PerformancePeriod period = request == null || request.period() == null
                ? PerformancePeriod.LAST_COMPLETE_MONTH : request.period();
        if (period == PerformancePeriod.LATEST_MONTH_WITH_TRADES) {
            return toolSupport.execute("queryPerformanceAnalysis", factQueryService::latestPerformanceAnalysis);
        }
        YearMonth yearMonth = switch (period) {
            case CURRENT_MONTH -> YearMonth.now();
            case LAST_COMPLETE_MONTH -> YearMonth.now().minusMonths(1);
            case SPECIFIC_MONTH -> parseYearMonth(request.yearMonth());
            case LATEST_MONTH_WITH_TRADES -> throw new IllegalStateException("已由专用查询分支处理");
        };
        return toolSupport.execute("queryPerformanceAnalysis", () -> factQueryService.performanceAnalysis(yearMonth));
    }

    private YearMonth parseYearMonth(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("SPECIFIC_MONTH 必须提供 yyyy-MM 格式的 yearMonth");
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("yearMonth 必须使用 yyyy-MM 格式", exception);
        }
    }

    /** 月度收益查询周期。 */
    public enum PerformancePeriod {
        CURRENT_MONTH,
        LAST_COMPLETE_MONTH,
        SPECIFIC_MONTH,
        LATEST_MONTH_WITH_TRADES
    }

    /** 月度收益分析请求。 */
    public record PerformanceAnalysisRequest(PerformancePeriod period, String yearMonth) {
    }
}
// AI_GENERATE_END -----