package com.stock.strategyAnalysis.entity;

import com.stock.strategyAnalysis.enums.SellTriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 卖出决策实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellDecision {
    
    /**
     * 决策ID
     */
    private String decisionId;
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 是否应该卖出
     */
    private boolean shouldSell;
    
    /**
     * 触发类型
     */
    private SellTriggerType triggerType;
    
    /**
     * 综合得分 [0-100]
     */
    private int score;
    
    /**
     * 当前价格
     */
    private double currentPrice;
    
    /**
     * 止损价格
     */
    private double stopLossPrice;
    
    /**
     * 决策原因
     */
    private String reason;
    
    /**
     * 决策时间
     */
    private LocalDateTime timestamp;
    
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
}