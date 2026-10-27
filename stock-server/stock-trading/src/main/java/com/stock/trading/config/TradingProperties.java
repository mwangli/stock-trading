// AI_GENERATE_START-----------
package com.stock.trading.config;

import lombok.Data;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 真实交易运行配置。
 * 交易同步参数。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading")
public class TradingProperties {

    /** 五年历史同步中相邻月份之间的请求间隔，单位为毫秒。 */
    private int historyMonthIntervalMs = 500;

}
// AI_GENERATE_END-----------
