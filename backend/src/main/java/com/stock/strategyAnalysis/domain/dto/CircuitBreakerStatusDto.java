package com.stock.strategyAnalysis.domain.dto;

import com.stock.strategyAnalysis.domain.entity.CircuitBreakerState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CircuitBreakerStatusDto {

    private CircuitBreakerState state;
    private boolean triggered;
    private int failureCount;
    private int failureThreshold;
    private LocalDateTime triggerTime;
    private LocalDateTime estimatedRecoverTime;
    private Map<String, Integer> indicatorFailureCounts;
}
