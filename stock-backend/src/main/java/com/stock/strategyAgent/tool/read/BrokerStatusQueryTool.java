// AI_GENERATE_START -
package com.stock.strategyAgent.tool.read;

import com.stock.strategyAgent.tool.AgentToolSupport;
import com.stock.tradingExecutor.domain.dto.BrokerReadStatusDto;
import com.stock.tradingExecutor.service.BrokerReadService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 券商只读网关状态工具。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Component
@RequiredArgsConstructor
public class BrokerStatusQueryTool {

    private final BrokerReadService brokerReadService;
    private final AgentToolSupport toolSupport;

    /**
     * 查询券商认证状态和真实写入门禁，不返回账户标识或凭据。
     *
     * @return 券商只读状态
     */
    @Tool(name = "queryBrokerStatus", value = "查询券商认证状态和真实写入门禁，不返回账户标识或凭据")
    public BrokerStatusResult query() {
        return toolSupport.execute("queryBrokerStatus", () -> {
            BrokerReadStatusDto status = brokerReadService.getStatus();
            return new BrokerStatusResult(status.getBrokerName(), status.getTradingMode(),
                    status.isAuthenticated(), status.isRealWriteEnabled(), status.isLiveWriteAllowed(),
                    LocalDateTime.now());
        });
    }

    /** 券商只读状态结果。 */
    public record BrokerStatusResult(String brokerName, String tradingMode, boolean authenticated,
                                     boolean realWriteEnabled, boolean liveWriteAllowed, LocalDateTime asOf) {
    }
}
// AI_GENERATE_END -
