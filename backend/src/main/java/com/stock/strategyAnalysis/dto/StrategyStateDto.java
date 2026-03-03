package com.stock.strategyAnalysis.dto;

import com.stock.strategyAnalysis.enums.StrategyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 策略状态DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyStateDto {
    
    /**
     * 策略是否启用
     */
    private boolean enabled;
    
    /**
     * 当前策略模式
     */
    private StrategyMode currentMode;
    
    /**
     * 熔断器状态
     */
    private CircuitBreakerStatusDto circuitBreaker;
    
    /**
     * 已禁用的指标列表
     */
    private List<String> disabledIndicators;
    
    /**
     * 最后切换时间
     */
    private LocalDateTime lastSwitchTime;
    
    /**
     * 最后切换原因
     */
    private String lastSwitchReason;
    
    /**
     * 当前配置版本
     */
    private String configVersion;
}