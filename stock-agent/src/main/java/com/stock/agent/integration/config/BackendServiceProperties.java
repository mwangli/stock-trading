// AI_GENERATE_START ---
package com.stock.agent.integration.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Backend 内部事实服务连接配置。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Component
@ConfigurationProperties(prefix = "backend.service")
public class BackendServiceProperties {

    /** 是否启用 Backend 权威事实源访问。 */
    private boolean enabled = true;

    /** Backend 服务根地址。 */
    private String baseUrl = "http://localhost:8080";

    /** 建连超时，单位毫秒。 */
    private int connectTimeoutMs = 3000;

    /** 读取超时，单位毫秒。 */
    private int readTimeoutMs = 10000;
}
// AI_GENERATE_END --
