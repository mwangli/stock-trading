// AI_GENERATE_START -
package com.stock.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 交易门禁服务连接配置。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.service")
public class AgentServiceProperties {

    /** 是否启用远程 Agent 门禁。 */
    private boolean enabled = true;

    /** Agent 服务根地址。 */
    private String baseUrl = "http://localhost:8081";

    /** 建连超时，单位毫秒。 */
    private int connectTimeoutMs = 2000;

    /** 读取超时，单位毫秒。 */
    private int readTimeoutMs = 5000;
}
// AI_GENERATE_END -
