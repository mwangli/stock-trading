// AI_GENERATE_START--------
package com.stock.agent.tooling.tool.read;

import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.model.TradingCalendarFact;
import com.stock.agent.tooling.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 交易日历只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class TradingCalendarQueryTool {

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 查询指定日期的已确认交易日状态。
     *
     * @param request 查询请求
     * @return 交易日状态
     */
    @Tool(name = "queryTradingCalendar", value = "查询指定日期的已确认交易日状态")
    public TradingCalendarResult query(TradingCalendarRequest request) {
        if (request == null || request.tradeDate() == null) {
            throw new IllegalArgumentException("tradeDate 不能为空");
        }
        return toolSupport.execute("queryTradingCalendar", () -> {
            TradingCalendarFact fact = factQueryPort.tradingCalendar(request.tradeDate());
            return new TradingCalendarResult(fact.tradeDate(), fact.tradingDay(), fact.confirmed());
        });
    }

    /**
     * 解析指定日期之后的首个确认交易日，避免周末直接作为预测目标日。
     *
     * @param date 基准日期
     * @return 首个确认交易日
     */
    public LocalDate nextConfirmedTradingDay(LocalDate date) {
        if (date == null) {
            throw new IllegalArgumentException("date 不能为空");
        }
        return factQueryPort.tradingCalendar(date).nextConfirmedTradingDay();
    }

    /** 交易日历查询请求。 */
    public record TradingCalendarRequest(LocalDate tradeDate) {
    }

    /** 交易日历查询结果。 */
    public record TradingCalendarResult(LocalDate tradeDate, boolean tradingDay, boolean confirmed) {
    }
}
// AI_GENERATE_END-------
