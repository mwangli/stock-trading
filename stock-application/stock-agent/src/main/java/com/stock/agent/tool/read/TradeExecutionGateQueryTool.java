// AI_GENERATE_START----
package com.stock.agent.tool.read;

import com.stock.data.service.TradingCalendarService;
import com.stock.strategy.engine.StockSelector;
import com.stock.agent.tool.AgentToolSupport;
import com.stock.trading.execution.BrokerAdapter;
import com.stock.trading.execution.TradingProperties;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
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

    private final StockSelector stockSelector;
    private final TradingCalendarService tradingCalendarService;
    private final TradingProperties tradingProperties;
    private final BrokerAdapter brokerAdapter;
    private final AgentToolSupport toolSupport;

    /**
     * 查询当前候选、交易日、券商会话和真实写入门禁，供解释未生成买入计划的原因。
     *
     * @return 执行门禁摘要
     */
    @Tool(name = "queryTradeExecutionGate", value = "查询候选、交易日、券商会话和真实交易写入门禁")
    public TradeExecutionGateResult query() {
        return toolSupport.execute("queryTradeExecutionGate", () -> {
            LocalDate targetTradeDate = stockSelector.getCurrentTargetTradeDate();
            boolean tradingDay = tradingCalendarService.isTradingDay(targetTradeDate);
            boolean brokerAuthenticated = brokerAdapter.isAuthenticated();
            boolean realWriteEnabled = tradingProperties.isLiveWriteAllowed();
            int candidateCount = stockSelector.getExecutableRankings(targetTradeDate).size();
            List<String> blockedReasons = new ArrayList<>();
            if (!tradingDay) {
                blockedReasons.add("目标交易日不是已确认交易日");
            }
            if (candidateCount == 0) {
                blockedReasons.add("当前目标交易日没有可执行候选");
            }
            if (!brokerAuthenticated) {
                blockedReasons.add("券商会话未认证");
            }
            if (!realWriteEnabled) {
                blockedReasons.add("真实交易写入总门禁未开启");
            }
            return new TradeExecutionGateResult(targetTradeDate, tradingDay, candidateCount,
                    brokerAuthenticated, realWriteEnabled, blockedReasons.isEmpty() ? "READY" : "BLOCKED",
                    List.copyOf(blockedReasons));
        });
    }

    /** 执行门禁摘要。 */
    public record TradeExecutionGateResult(LocalDate targetTradeDate, boolean tradingDay,
                                           int executableCandidateCount, boolean brokerAuthenticated,
                                           boolean realWriteEnabled, String status,
                                           List<String> blockedReasons) {
    }
}
// AI_GENERATE_END----
