package com.stock.strategyAnalysis.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略得分实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyScore {
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 移动止损是否触发
     */
    private boolean trailingStopTriggered;
    
    /**
     * RSI值
     */
    private double rsiValue;
    
    /**
     * 成交量比率
     */
    private double volumeRatio;
    
    /**
     * 布林带是否突破
     */
    private boolean bollingerBreakout;
    
    /**
     * 综合得分 [0-100]
     */
    private int totalScore;
    
    /**
     * 动态阈值
     */
    private int dynamicThreshold;
    
    /**
     * 计算时间
     */
    private LocalDateTime calculateTime;
}