// AI_GENERATE_START--------
package com.stock.agent.tool;

import com.stock.agent.service.AgentFactQueryService;
import com.stock.agent.model.BrokerFacts;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 券商账户事实只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class BrokerFactQueryTool {

    private final AgentFactQueryService factQueryService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询账户、持仓、当日订单和成交的脱敏摘要。
     *
     * @return 券商事实摘要
     */
    @Tool(name = "queryBrokerFacts", value = "查询账户、持仓、当日订单和成交的脱敏摘要")
    public BrokerFactResult query() {
        return toolSupport.execute("queryBrokerFacts", () -> {
            BrokerFacts facts = factQueryService.brokerFacts();
            return new BrokerFactResult(facts.availableCash(), facts.totalAssets(), facts.positionCount(),
                    facts.todayOrderCount(), facts.todayFillCount(), facts.asOf());
        });
    }

    /** 券商事实脱敏结果。 */
    public record BrokerFactResult(BigDecimal availableCash, BigDecimal totalAssets, int positionCount,
                                   int todayOrderCount, int todayFillCount, LocalDateTime asOf) {
    }
}
// AI_GENERATE_END-------