package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录 DTO
 *
 * 将原先以 Map 形式返回的交易记录结构化为明确字段，
 * 便于前后端约定和演进。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingRecordDto {

    private String id;

    private String code;

    private String name;

    /**
     * 交易方向：BUY / SELL
     */
    private String direction;

    private BigDecimal price;

    private Integer quantity;

    private BigDecimal amount;

    private String status;

    private LocalDateTime time;
}

