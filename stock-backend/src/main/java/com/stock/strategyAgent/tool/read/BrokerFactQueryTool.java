// AI_GENERATE_START -
package com.stock.strategyAgent.tool.read;

import com.stock.tradingExecutor.domain.vo.AccountStatus;
import com.stock.tradingExecutor.domain.vo.BrokerFillSnapshot;
import com.stock.tradingExecutor.domain.vo.BrokerOrderSnapshot;
import com.stock.tradingExecutor.domain.entity.Position;
import com.stock.tradingExecutor.execution.BrokerAdapter;
import com.stock.strategyAgent.tool.ToolDefinition;
import com.stock.strategyAgent.tool.ToolGateway;
import com.stock.strategyAgent.tool.ToolPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
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
public class BrokerFactQueryTool implements InitializingBean {

    private final ToolGateway toolGateway;
    private final BrokerAdapter brokerAdapter;

    /**
     * 注册券商事实查询工具。
     */
    @Override
    public void afterPropertiesSet() {
        toolGateway.register(ToolDefinition.builder()
                        .name("queryBrokerFacts")
                        .version("v1")
                        .description("查询账户、持仓、当日订单和成交的脱敏摘要")
                        .requiredPermission(ToolPermission.READ_ONLY)
                        .readOnly(true)
                        .maxResultSize(6000)
                        .timeoutMs(5000L)
                        .build(),
                (input, context) -> query());
    }

    private BrokerFactResult query() {
        AccountStatus account = brokerAdapter.getAccountInfo();
        List<Position> positions = brokerAdapter.getPositions();
        List<BrokerOrderSnapshot> orders = brokerAdapter.getTodayOrderSnapshots();
        List<BrokerFillSnapshot> fills = brokerAdapter.getTodayFillSnapshots();
        return new BrokerFactResult(account == null ? null : account.getAvailableCash(),
                account == null ? null : account.getTotalAssets(), positions.size(), orders.size(),
                fills.size(), LocalDateTime.now());
    }

    /** 券商事实脱敏结果。 */
    public record BrokerFactResult(BigDecimal availableCash, BigDecimal totalAssets, int positionCount,
                                   int todayOrderCount, int todayFillCount, LocalDateTime asOf) {
    }
}
// AI_GENERATE_END -
