// AI_GENERATE_START -
package com.stock.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 服务间内部接口鉴权配置。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Component
@ConfigurationProperties(prefix = "internal.api")
public class InternalApiProperties {

    /** 内部接口共享令牌，仅允许从运行环境注入。 */
    private String token = "";
}
// AI_GENERATE_END -
