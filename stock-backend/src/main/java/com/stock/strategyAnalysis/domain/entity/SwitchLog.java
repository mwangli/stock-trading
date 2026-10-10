package com.stock.strategyAnalysis.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略切换日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SwitchLog {

    private String logId;
    private LocalDateTime switchTime;
    private SwitchType switchType;
    private StrategyMode fromMode;
    private StrategyMode toMode;
    private String reason;
    private String trigger;
    private String operator;
}
