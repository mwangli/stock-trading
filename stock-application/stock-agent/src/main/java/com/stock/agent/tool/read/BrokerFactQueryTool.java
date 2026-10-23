// AI_GENERATE_START-----
package com.stock.agent.tool.read;

import com.stock.trading.vo.AccountStatus;
import com.stock.trading.vo.BrokerFillSnapshot;
import com.stock.trading.vo.BrokerOrderSnapshot;
import com.stock.trading.entity.Position;
import com.stock.trading.execution.BrokerAdapter;
import com.stock.agent.tool.AgentToolSupport;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 券商账户事实只读工具。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Component
@RequiredArgsConstructor
public class BrokerFactQueryTool {

    private final BrokerAdapter brokerAdapter;
    private final AgentToolSupport toolSupport;

    /**
     * 查询账户、持仓、当日订单和成交的脱敏摘要。
     *
     * @return 券商事实摘要
     */
    @Tool(name = "queryBrokerFacts", value = "查询账户、持仓、当日订单和成交的脱敏摘要")
    public BrokerFactResult query() {
        return toolSupport.execute("queryBrokerFacts", () -> {
            AccountStatus account = brokerAdapter.getAccountInfo();
            List<Position> positions = brokerAdapter.getPositions();
            List<BrokerOrderSnapshot> orders = brokerAdapter.getTodayOrderSnapshots();
            List<BrokerFillSnapshot> fills = brokerAdapter.getTodayFillSnapshots();
            return new BrokerFactResult(account == null ? null : account.getAvailableCash(),
                    account == null ? null : account.getTotalAssets(), positions.size(), orders.size(),
                    fills.size(), LocalDateTime.now());
        });
    }

    /** 券商事实脱敏结果。 */
    public record BrokerFactResult(BigDecimal availableCash, BigDecimal totalAssets, int positionCount,
                                   int todayOrderCount, int todayFillCount, LocalDateTime asOf) {
    }
}
// AI_GENERATE_END-----
