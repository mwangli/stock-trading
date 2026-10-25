// AI_GENERATE_START --
package com.stock.agent.tooling.tool.read;

import com.stock.agent.tooling.model.PerformanceAnalysisFact;
import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.tool.AgentToolContextHolder;
import com.stock.agent.tooling.tool.AgentToolSupport;
import com.stock.agent.tooling.tool.ToolExecutionContext;
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

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 查询指定自然月的已实现收益、费用和主要损益交易。
     *
     * @param request 月份请求；为空时查询上一个完整自然月
     * @return 月度收益分析事实
     */
    @Tool(name = "queryPerformanceAnalysis", value = "查询指定自然月的已实现收益、费用、胜率以及主要盈利和亏损交易；上个月使用上一完整自然月")
    public PerformanceAnalysisFact query(PerformanceAnalysisRequest request) {
        ToolExecutionContext context = AgentToolContextHolder.current();
        YearMonth yearMonth = context != null && context.getPerformanceYearMonth() != null
                ? context.getPerformanceYearMonth()
                : parseYearMonth(request == null ? null : request.yearMonth());
        return toolSupport.execute("queryPerformanceAnalysis", () -> factQueryPort.performanceAnalysis(yearMonth));
    }

    private YearMonth parseYearMonth(String value) {
        if (!StringUtils.hasText(value)) {
            return YearMonth.now().minusMonths(1);
        }
        try {
            return YearMonth.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("yearMonth 必须使用 yyyy-MM 格式", exception);
        }
    }

    /** 月度收益分析请求。 */
    public record PerformanceAnalysisRequest(String yearMonth) {
    }
}
// AI_GENERATE_END --
