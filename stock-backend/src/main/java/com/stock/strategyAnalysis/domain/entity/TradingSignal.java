package com.stock.strategyAnalysis.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交易信号实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingSignal {

    private String signalId;
    private String stockCode;
    private String stockName;
    private Signal signalType;
    private int strength;
    private double confidence;
    private String reason;
    private LocalDateTime generateTime;
    private boolean executed;
    private LocalDateTime executeTime;
}
