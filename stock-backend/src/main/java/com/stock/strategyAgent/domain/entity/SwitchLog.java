// AI_GENERATE_START --
package com.stock.strategyAgent.domain.entity;

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
// AI_GENERATE_END --
