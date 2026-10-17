// AI_GENERATE_START -
package com.stock.strategyAgent.tool.read;

import com.stock.dataCollector.service.TradingCalendarService;
import com.stock.strategyAgent.tool.ToolDefinition;
import com.stock.strategyAgent.tool.ToolGateway;
import com.stock.strategyAgent.tool.ToolPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
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
public class TradingCalendarQueryTool implements InitializingBean {

    private final ToolGateway toolGateway;
    private final TradingCalendarService tradingCalendarService;

    /**
     * 注册交易日历工具。
     */
    @Override
    public void afterPropertiesSet() {
        toolGateway.register(ToolDefinition.builder()
                        .name("queryTradingCalendar")
                        .version("v1")
                        .description("查询指定日期的已确认交易日状态")
                        .requiredPermission(ToolPermission.READ_ONLY)
                        .readOnly(true)
                        .maxResultSize(2000)
                        .timeoutMs(3000L)
                        .build(),
                (input, context) -> query(input));
    }

    private TradingCalendarResult query(Object input) {
        if (!(input instanceof TradingCalendarRequest request) || request.tradeDate() == null) {
            throw new IllegalArgumentException("tradeDate 不能为空");
        }
        LocalDate date = request.tradeDate();
        return new TradingCalendarResult(date, tradingCalendarService.isTradingDay(date),
                tradingCalendarService.isConfirmedDate(date));
    }

    /** 交易日历查询请求。 */
    public record TradingCalendarRequest(LocalDate tradeDate) {
    }

    /** 交易日历查询结果。 */
    public record TradingCalendarResult(LocalDate tradeDate, boolean tradingDay, boolean confirmed) {
    }
}
// AI_GENERATE_END -
