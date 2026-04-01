package com.stock.tradingExecutor.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 历史订单响应DTO
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Data
public class HistoryOrderDTO {

    private Long id;

    private String orderDate;

    private String orderNo;

    private String marketType;

    private String stockAccount;

    private String stockCode;

    private String stockName;

    private String direction;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal amount;

    private String serialNo;

    private String orderTime;

    private String remark;

    private String fullName;

    private String syncBatchNo;

    private LocalDateTime lastSyncTime;

    private LocalDateTime orderSubmitTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
