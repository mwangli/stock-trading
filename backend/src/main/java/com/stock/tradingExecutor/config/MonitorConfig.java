package com.stock.tradingExecutor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 价格监控配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading.monitor")
public class MonitorConfig {
    /**
     * 采样间隔(秒)
     */
    private Long sampleIntervalSeconds = 20L;
    
    /**
     * 最少采样次数
     */
    private Integer minSampleCount = 5;
    
    /**
     * 买入阈值(均价-1%)
     */
    private Double buyThresholdPercent = -0.01;
    
    /**
     * 卖出阈值(均价+0.5%)
     */
    private Double sellThresholdPercent = 0.005;
    
    /**
     * 强制执行时间
     */
    private LocalTime forceExecuteTime = LocalTime.of(14, 55);
    
    /**
     * 买入截止时间
     */
    private LocalTime buyDeadLine = LocalTime.of(14, 50);
    
    /**
     * 卖出截止时间
     */
    private LocalTime sellDeadLine = LocalTime.of(14, 57);
}