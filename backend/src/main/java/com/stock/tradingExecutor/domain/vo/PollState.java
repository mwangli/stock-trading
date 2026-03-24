package com.stock.tradingExecutor.domain.vo;

import com.stock.tradingExecutor.domain.entity.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮询状态
 */
@Data
public class PollState {

    private String orderId;
    private int pollCount;
    private int retryCount;
    private OrderStatus lastStatus;
    private LocalDateTime startTime;
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

    public void incrementPollCount() {
        this.pollCount++;
        this.lastPollTime = LocalDateTime.now();
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }
}
