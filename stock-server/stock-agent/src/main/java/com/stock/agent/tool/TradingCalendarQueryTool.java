// AI_GENERATE_START------------
package com.stock.agent.tool;

import com.stock.agent.service.AgentFactQueryService;
import com.stock.agent.model.TradingCalendarFact;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 工作日规则只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class TradingCalendarQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询指定日期是否为周一至周五。
     *
     * @param request 查询请求
     * @return 交易日状态
     */
    @Tool(name = "queryTradingCalendar", value = "查询指定日期是否为周一至周五")
    public TradingCalendarResult query(TradingCalendarRequest request) {
        if (request == null || request.tradeDate() == null) {
            throw new IllegalArgumentException("tradeDate 不能为空");
        }
        return toolSupport.execute("queryTradingCalendar", () -> {
            TradingCalendarFact fact = factQueryService.tradingCalendar(request.tradeDate());
            return new TradingCalendarResult(fact.tradeDate(), fact.tradingDay(), fact.confirmed(),
                    fact.nextConfirmedTradingDay());
        });
    }

    /** 工作日规则查询请求。 */
    public record TradingCalendarRequest(LocalDate tradeDate) {
    }

    /**
     * 工作日规则查询结果。
     *
     * @param tradeDate 查询日期
     * @param tradingDay 是否为周一至周五
     * @param confirmed 日期非空时固定为 true
     * @param nextConfirmedTradingDay 当日或之后首个周一至周五
     */
    public record TradingCalendarResult(
            LocalDate tradeDate, boolean tradingDay, boolean confirmed,
            LocalDate nextConfirmedTradingDay) {
    }
}
// AI_GENERATE_END-----------
