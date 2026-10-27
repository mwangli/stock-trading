// AI_GENERATE_START- --
package com.stock.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tushare 免费日线接口配置。
 * Token 只从环境变量或外部配置读取，不写入日志和持久化数据。
 *
 * @author mwangli
 * @since 2026-10-26
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.market.tushare")
public class TushareProperties {

    /** 是否允许使用 Tushare 免费日线回填 V6 模型数据。 */
    private boolean enabled = true;

    /** Tushare HTTP API 地址。 */
    private String endpoint = "http://api.tushare.pro";

    /** Tushare API Token。 */
    private String token;

    /** 主动限速的每分钟最大请求数，默认低于免费账户上限。 */
    private int requestsPerMinute = 45;

    /**
     * 判断免费日线数据源是否具备调用条件。
     *
     * @return 已启用且地址、Token 均有效时返回 true
     */
    public boolean isConfigured() {
        return enabled && endpoint != null && !endpoint.isBlank()
                && token != null && !token.isBlank();
    }
}
// AI_GENERATE_END- --