package com.stock.strategyAnalysis.entity;

import com.stock.strategyAnalysis.enums.StrategyMode;
import com.stock.strategyAnalysis.enums.SwitchType;
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
    
    /**
     * 日志ID
     */
    private String logId;
    
    /**
     * 切换时间
     */
    private LocalDateTime switchTime;
    
    /**
     * 切换类型
     */
    private SwitchType switchType;
    
    /**
     * 切换前模式
     */
    private StrategyMode fromMode;
    
    /**
     * 切换后模式
     */
    private StrategyMode toMode;
    
    /**
     * 切换原因
     */
    private String reason;
    
    /**
     * 触发源
     */
    private String trigger;
    
    /**
     * 操作人/系统标识
     */
    private String operator;
}