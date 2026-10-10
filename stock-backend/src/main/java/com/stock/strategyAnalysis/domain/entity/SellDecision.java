package com.stock.strategyAnalysis.domain.entity;

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

    private String decisionId;
    private String stockCode;
    private boolean shouldSell;
    private SellTriggerType triggerType;
    private int score;
    private double currentPrice;
    private double stopLossPrice;
    private String reason;
    private LocalDateTime timestamp;
    private boolean trailingStopTriggered;
    private double rsiValue;
    private double volumeRatio;
    private boolean bollingerBreakout;
}
