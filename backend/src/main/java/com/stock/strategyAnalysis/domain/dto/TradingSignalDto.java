package com.stock.strategyAnalysis.domain.dto;

import com.stock.strategyAnalysis.domain.entity.Signal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交易信号 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingSignalDto {

    private String signalId;
    private String stockCode;
    private String stockName;
    private Signal signalType;
    private int strength;
    private double confidence;
    private String reason;
    private LocalDateTime generateTime;
}
