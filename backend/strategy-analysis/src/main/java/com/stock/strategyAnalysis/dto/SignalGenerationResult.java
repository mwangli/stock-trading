package com.stock.strategyAnalysis.dto;

import com.stock.strategyAnalysis.enums.Signal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信号生成结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalGenerationResult {
    
    /**
     * 生成时间
     */
    private LocalDateTime generateTime;
    
    /**
     * 买入信号列表
     */
    private List<TradingSignalDto> buySignals;
    
    /**
     * 卖出信号列表
     */
    private List<TradingSignalDto> sellSignals;
    
    /**
     * 买入信号数量
     */
    private int buyCount;
    
    /**
     * 卖出信号数量
     */
    private int sellCount;
    
    /**
     * 耗时（毫秒）
     */
    private long costTimeMs;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
}