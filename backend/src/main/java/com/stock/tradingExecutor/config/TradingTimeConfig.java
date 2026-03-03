package com.stock.tradingExecutor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 交易时间配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "trading.time")
public class TradingTimeConfig {
    /**
     * 早盘开始时间
     */
    private LocalTime morningStart = LocalTime.of(9, 30);
    
    /**
     * 早盘结束时间
     */
    private LocalTime morningEnd = LocalTime.of(11, 30);
    
    /**
     * 午盘开始时间
     */
    private LocalTime afternoonStart = LocalTime.of(13, 0);
    
    /**
     * 午盘结束时间
     */
    private LocalTime afternoonEnd = LocalTime.of(15, 0);
    
    /**
     * 买入截止时间
     */
    private LocalTime buyDeadLine = LocalTime.of(14, 50);
    
    /**
     * 卖出截止时间
     */
    private LocalTime sellDeadLine = LocalTime.of(14, 57);
    
    /**
     * 临近收盘判断时间
     */
    private LocalTime nearCloseTime = LocalTime.of(14, 55);
}