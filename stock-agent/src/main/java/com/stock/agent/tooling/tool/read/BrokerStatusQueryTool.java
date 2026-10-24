// AI_GENERATE_START------
package com.stock.agent.tooling.tool.read;

import com.stock.agent.tooling.tool.AgentToolSupport;
import com.stock.agent.tooling.port.ToolFactQueryPort;
import com.stock.agent.tooling.model.BrokerStatusFact;
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

    private final ToolFactQueryPort factQueryPort;
    private final AgentToolSupport toolSupport;

    /**
     * 查询券商认证状态和真实写入门禁，不返回账户标识或凭据。
     *
     * @return 券商只读状态
     */
    @Tool(name = "queryBrokerStatus", value = "查询券商认证状态和真实写入门禁，不返回账户标识或凭据")
    public BrokerStatusResult query() {
        return toolSupport.execute("queryBrokerStatus", () -> {
            BrokerStatusFact status = factQueryPort.brokerStatus();
            return new BrokerStatusResult(status.brokerName(), status.tradingMode(),
                    status.authenticated(), status.realWriteEnabled(), status.liveWriteAllowed(), status.asOf());
        });
    }

    /** 券商只读状态结果。 */
    public record BrokerStatusResult(String brokerName, String tradingMode, boolean authenticated,
                                     boolean realWriteEnabled, boolean liveWriteAllowed, LocalDateTime asOf) {
    }
}
// AI_GENERATE_END-----
