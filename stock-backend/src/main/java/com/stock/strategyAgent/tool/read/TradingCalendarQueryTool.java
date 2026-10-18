// AI_GENERATE_START --
package com.stock.strategyAgent.tool.read;

import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.strategyAgent.tool.AgentToolSupport;
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

    private final TradingCalendarService tradingCalendarService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询指定日期的已确认交易日状态。
     *
     * @param request 查询请求
     * @return 交易日状态
     */
    @Tool("查询指定日期的已确认交易日状态")
    public TradingCalendarResult query(TradingCalendarRequest request) {
        if (request == null || request.tradeDate() == null) {
            throw new IllegalArgumentException("tradeDate 不能为空");
        }
        return toolSupport.execute("queryTradingCalendar", () -> {
            LocalDate date = request.tradeDate();
            return new TradingCalendarResult(date, tradingCalendarService.isTradingDay(date),
                    tradingCalendarService.isConfirmedDate(date));
        });
    }

    /** 交易日历查询请求。 */
    public record TradingCalendarRequest(LocalDate tradeDate) {
    }

    /** 交易日历查询结果。 */
    public record TradingCalendarResult(LocalDate tradeDate, boolean tradingDay, boolean confirmed) {
    }
}
// AI_GENERATE_END --
