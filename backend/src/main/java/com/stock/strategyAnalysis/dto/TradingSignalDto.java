package com.stock.strategyAnalysis.dto;

import com.stock.strategyAnalysis.enums.Signal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交易信号DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradingSignalDto {
    
    /**
     * 信号ID
     */
    private String signalId;
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 股票名称
     */
    private String stockName;
    
    /**
     * 信号类型
     */
    private Signal signalType;
    
    /**
     * 信号强度 [0-100]
     */
    private int strength;
    
    /**
     * 置信度 [0-1]
     */
    private double confidence;
    
    /**
     * 信号原因
     */
    private String reason;
    
    /**
     * 生成时间
     */
    private LocalDateTime generateTime;
}