package com.stock.tradingExecutor.domain.vo;

import com.stock.tradingExecutor.domain.entity.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单结果
 */
@Data
@Builder
public class OrderResult {

    private boolean success;
    private String orderId;
    private String stockCode;
    private String stockName;
    private String direction;
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal amount;
    private BigDecimal fee;
    private OrderStatus status;
    private String message;
    private LocalDateTime submitTime;
    private LocalDateTime fillTime;

    public static OrderResult fail(String message) {
        return OrderResult.builder()
                .success(false)
                .message(message)
                .submitTime(LocalDateTime.now())
                .build();
    }

    public static OrderResult fail(String message, List<String> violations) {
        return OrderResult.builder()
                .success(false)
                .message(message + ": " + String.join(", ", violations))
                .submitTime(LocalDateTime.now())
                .build();
    }
}
