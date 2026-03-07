package com.stock.tradingExecutor.entity;

import com.stock.tradingExecutor.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮询状态
 */
@Data
public class PollState {
    /**
     * 订单ID
     */
    private String orderId;
    
    /**
     * 轮询次数
     */
    private int pollCount;
    
    /**
     * 重试次数
     */
    private int retryCount;
    
    /**
     * 最后状态
     */
    private OrderStatus lastStatus;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 最后轮询时间
     */
    private LocalDateTime lastPollTime;
    
    public PollState() {
        this.pollCount = 0;
        this.retryCount = 0;
        this.startTime = LocalDateTime.now();
    }
    
    public PollState(String orderId) {
        this();
        this.orderId = orderId;
    }
    
    /**
     * 增加轮询次数
     */
    public void incrementPollCount() {
        this.pollCount++;
        this.lastPollTime = LocalDateTime.now();
    }
    
    /**
     * 增加重试次数
     */
    public void incrementRetryCount() {
        this.retryCount++;
    }
}