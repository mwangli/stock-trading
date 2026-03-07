package com.stock.tradingExecutor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 订单轮询配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading.poller")
public class PollerConfig {
    /**
     * 轮询间隔(秒)
     */
    private Long pollIntervalSeconds = 10L;
    
    /**
     * 最大轮询次数
     */
    private Integer maxPollCount = 18;
    
    /**
     * 超时时间(秒)
     */
    private Long timeoutSeconds = 60L;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetryCount = 10;
}