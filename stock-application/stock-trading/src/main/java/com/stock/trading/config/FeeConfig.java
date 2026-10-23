// AI_GENERATE_START---
package com.stock.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 手续费配置
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading.fee")
public class FeeConfig {
    /**
     * 手续费率 (万分之五)
     */
    private Double feeRate = 0.0005;
    
    /**
     * 最低手续费
     */
    private Double minFee = 5.0;
}
// AI_GENERATE_END---
