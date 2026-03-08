package com.stock.strategyAnalysis.domain.dto;

import com.stock.strategyAnalysis.domain.entity.StrategyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyStateDto {

    private boolean enabled;
    private StrategyMode currentMode;
    private CircuitBreakerStatusDto circuitBreaker;
    private List<String> disabledIndicators;
    private LocalDateTime lastSwitchTime;
    private String lastSwitchReason;
    private String configVersion;
}
