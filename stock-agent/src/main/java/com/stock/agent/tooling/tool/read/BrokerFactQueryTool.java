// AI_GENERATE_START-------
package com.stock.agent.tooling.tool.read;

import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.model.BrokerFacts;
import com.stock.agent.tooling.tool.AgentToolSupport;
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

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 查询账户、持仓、当日订单和成交的脱敏摘要。
     *
     * @return 券商事实摘要
     */
    @Tool(name = "queryBrokerFacts", value = "查询账户、持仓、当日订单和成交的脱敏摘要")
    public BrokerFactResult query() {
        return toolSupport.execute("queryBrokerFacts", () -> {
            BrokerFacts facts = factQueryPort.brokerFacts();
            return new BrokerFactResult(facts.availableCash(), facts.totalAssets(), facts.positionCount(),
                    facts.todayOrderCount(), facts.todayFillCount(), facts.asOf());
        });
    }

    /** 券商事实脱敏结果。 */
    public record BrokerFactResult(BigDecimal availableCash, BigDecimal totalAssets, int positionCount,
                                   int todayOrderCount, int todayFillCount, LocalDateTime asOf) {
    }
}
// AI_GENERATE_END------
