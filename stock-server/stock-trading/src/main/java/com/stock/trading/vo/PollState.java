// AI_GENERATE_START---------
package com.stock.trading.vo;

import com.stock.trading.entity.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮询状态
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class PollState {

    /**
     * 订单唯一标识。
     */
    private String orderId;
    /**
     * 已执行的订单状态轮询次数。
     */
    private int pollCount;
    /**
     * 订单状态轮询重试次数。
     */
    private int retryCount;
    /**
     * 最近一次查询到的订单状态。
     */
    private OrderStatus lastStatus;
    /**
     * 监控开始时间。
     */
    private LocalDateTime startTime;
    /**
     * 最近一次轮询时间。
     */
    private LocalDateTime lastPollTime;

    /**
     * 创建尚未绑定订单的初始轮询状态。
     */
    public PollState() {
        this.pollCount = 0;
        this.retryCount = 0;
        this.startTime = LocalDateTime.now();
    }

    /**
     * 创建指定订单的初始轮询状态。
     *
     * @param orderId 订单唯一标识
     */
    public PollState(String orderId) {
        this();
        this.orderId = orderId;
    }

    /** 增加一次订单状态轮询计数。 */
    public void incrementPollCount() {
        this.pollCount++;
        this.lastPollTime = LocalDateTime.now();
    }

    /** 增加一次订单轮询重试计数。 */
    public void incrementRetryCount() {
        this.retryCount++;
    }
}

// AI_GENERATE_END---------
