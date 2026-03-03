package com.stock.strategyAnalysis.switcher;

import com.stock.strategyAnalysis.config.StrategyStateManager;
import com.stock.strategyAnalysis.dto.CircuitBreakerStatusDto;
import com.stock.strategyAnalysis.enums.CircuitBreakerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 熔断器
 * 监控策略失败情况，触发熔断保护
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreaker {

    private final StrategyStateManager stateManager;

    /**
     * 记录失败
     */
    public void recordFailure(String indicator) {
        stateManager.recordIndicatorFailure(indicator);
    }

    /**
     * 检查是否触发熔断
     */
    public boolean isTriggered() {
        CircuitBreakerStatusDto status = stateManager.getCircuitBreakerStatus();
        return status.isTriggered();
    }

    /**
     * 获取熔断状态
     */
    public CircuitBreakerState getState() {
        return stateManager.getCircuitBreakerStatus().getState();
    }

    /**
     * 重置熔断器
     */
    public void reset() {
        stateManager.resetCircuitBreaker();
        log.info("熔断器已重置");
    }

    /**
     * 定时检查熔断恢复
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void checkRecovery() {
        CircuitBreakerStatusDto status = stateManager.getCircuitBreakerStatus();
        
        if (status.getState() == CircuitBreakerState.OPEN) {
            LocalDateTime recoverTime = status.getEstimatedRecoverTime();
            if (recoverTime != null && LocalDateTime.now().isAfter(recoverTime)) {
                log.info("熔断恢复时间已到，重置熔断器");
                reset();
            }
        }
    }
}