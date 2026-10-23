// AI_GENERATE_START-----
package com.stock.agent.tool.read;

import com.stock.agent.client.BackendFactsClient;
import com.stock.agent.client.dto.TradeExecutionGateFact;
import com.stock.agent.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 真实交易执行门禁只读工具。
 *
 * @author mwangli
 * @since 2026-10-19
 */
@Component
@RequiredArgsConstructor
public class TradeExecutionGateQueryTool {

    private final BackendFactsClient backendFactsClient;
    private final AgentToolSupport toolSupport;

    /**
     * 查询当前候选、交易日、券商会话和真实写入门禁，供解释未生成买入计划的原因。
     *
     * @return 执行门禁摘要
     */
    @Tool(name = "queryTradeExecutionGate", value = "查询候选、交易日、券商会话和真实交易写入门禁")
    public TradeExecutionGateResult query() {
        return toolSupport.execute("queryTradeExecutionGate", () -> {
            TradeExecutionGateFact fact = backendFactsClient.tradeExecutionGate();
            return new TradeExecutionGateResult(fact.targetTradeDate(), fact.tradingDay(),
                    fact.executableCandidateCount(), fact.brokerAuthenticated(), fact.realWriteEnabled(),
                    fact.status(), fact.blockedReasons());
        });
    }

    /** 执行门禁摘要。 */
    public record TradeExecutionGateResult(LocalDate targetTradeDate, boolean tradingDay,
                                           int executableCandidateCount, boolean brokerAuthenticated,
                                           boolean realWriteEnabled, String status,
                                           List<String> blockedReasons) {
    }
}
// AI_GENERATE_END-----
