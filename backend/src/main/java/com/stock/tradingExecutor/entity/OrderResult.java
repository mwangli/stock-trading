package com.stock.tradingExecutor.entity;

import com.stock.tradingExecutor.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单结果
 */
@Data
@Builder
public class OrderResult {
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 委托编号
     */
    private String orderId;
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 股票名称
     */
    private String stockName;
    
    /**
     * 买卖方向 BUY/SELL
     */
    private String direction;
    
    /**
     * 委托价格
     */
    private BigDecimal price;
    
    /**
     * 委托数量
     */
    private Integer quantity;
    
    /**
     * 成交金额
     */
    private BigDecimal amount;
    
    /**
     * 手续费
     */
    private BigDecimal fee;
    
    /**
     * 订单状态
     */
    private OrderStatus status;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 提交时间
     */
    private LocalDateTime submitTime;
    
    /**
     * 成交时间
     */
    private LocalDateTime fillTime;
    
    /**
     * 创建失败结果
     */
    public static OrderResult fail(String message) {
        return OrderResult.builder()
                .success(false)
                .message(message)
                .submitTime(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建失败结果(带违规列表)
     */
    public static OrderResult fail(String message, java.util.List<String> violations) {
        return OrderResult.builder()
                .success(false)
                .message(message + ": " + String.join(", ", violations))
                .submitTime(LocalDateTime.now())
                .build();
    }
}