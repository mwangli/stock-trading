// AI_GENERATE_START --
package com.stock.strategyAnalysis.engine;

import com.stock.strategyAnalysis.config.StrategyStateManager;
import com.stock.strategyAnalysis.domain.dto.CircuitBreakerStatusDto;
import com.stock.strategyAnalysis.domain.entity.CircuitBreakerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 策略熔断器。
 * 监控策略失败情况、暴露熔断状态，并在恢复时间到达后解除熔断。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CircuitBreaker {

    private final StrategyStateManager stateManager;

    /**
     * 记录指定指标执行失败。
     *
     * @param indicator 指标标识
     */
    public void recordFailure(String indicator) {
        stateManager.recordIndicatorFailure(indicator);
    }

    /**
     * 检查当前是否已触发熔断。
     *
     * @return true 表示已触发熔断
     */
    public boolean isTriggered() {
        CircuitBreakerStatusDto status = stateManager.getCircuitBreakerStatus();
        return status.isTriggered();
    }

    /**
     * 获取当前熔断状态。
     *
     * @return 熔断状态
     */
    public CircuitBreakerState getState() {
        return stateManager.getCircuitBreakerStatus().getState();
    }

    /**
     * 重置熔断器并恢复策略执行。
     */
    public void reset() {
        stateManager.resetCircuitBreaker();
        log.info("熔断器已重置");
    }

    @org.springframework.beans.factory.annotation.Value("${app.scheduling.enabled:true}")
    private boolean schedulingEnabled;

    /**
     * 检查熔断恢复时间是否已到。
     * 调用周期由统一的 JobSchedulerService 管理。
     */
    public void checkRecovery() {
        if (!schedulingEnabled) {
            return;
        }
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
// AI_GENERATE_END --
