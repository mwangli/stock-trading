package com.stock.strategyAnalysis.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略得分 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyScore {

    private String stockCode;
    private boolean trailingStopTriggered;
    private double rsiValue;
    private double volumeRatio;
    private boolean bollingerBreakout;
    private int totalScore;
    private int dynamicThreshold;
    private LocalDateTime calculateTime;
}
