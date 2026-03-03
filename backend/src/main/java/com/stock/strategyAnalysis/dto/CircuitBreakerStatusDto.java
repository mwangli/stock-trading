package com.stock.strategyAnalysis.dto;

import com.stock.strategyAnalysis.enums.CircuitBreakerState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 熔断器状态DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerStatusDto {
    
    /**
     * 熔断器状态
     */
    private CircuitBreakerState state;
    
    /**
     * 是否已触发
     */
    private boolean triggered;
    
    /**
     * 失败计数
     */
    private int failureCount;
    
    /**
     * 失败阈值
     */
    private int failureThreshold;
    
    /**
     * 触发时间
     */
    private LocalDateTime triggerTime;
    
    /**
     * 预计恢复时间
     */
    private LocalDateTime estimatedRecoverTime;
    
    /**
     * 各指标失败计数
     */
    private Map<String, Integer> indicatorFailureCounts;
}