package com.stock.strategyAnalysis.config;

import com.stock.strategyAnalysis.dto.CircuitBreakerStatusDto;
import com.stock.strategyAnalysis.dto.StrategyStateDto;
import com.stock.strategyAnalysis.entity.SwitchLog;
import com.stock.strategyAnalysis.enums.CircuitBreakerState;
import com.stock.strategyAnalysis.enums.StrategyMode;
import com.stock.strategyAnalysis.repository.SwitchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略状态管理器
 * 管理策略的运行状态、熔断状态等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyStateManager {

    private static final String REDIS_STATE_KEY = "strategy:state";
    private static final String REDIS_CIRCUIT_KEY = "strategy:circuit";
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final SwitchLogRepository switchLogRepository;

    /**
     * 获取当前策略状态
     */
    public StrategyStateDto getCurrentState() {
        Object cached = redisTemplate.opsForValue().get(REDIS_STATE_KEY);
        if (cached instanceof StrategyStateDto) {
            return (StrategyStateDto) cached;
        }
        
        // 返回默认状态
        return StrategyStateDto.builder()
                .enabled(true)
                .currentMode(StrategyMode.BALANCED)
                .disabledIndicators(new ArrayList<>())
                .lastSwitchTime(LocalDateTime.now())
                .lastSwitchReason("初始化")
                .configVersion("1.0.0")
                .build();
    }

    /**
     * 更新策略模式
     */
    public void updateMode(StrategyMode newMode, String reason) {
        StrategyStateDto state = getCurrentState();
        StrategyMode oldMode = state.getCurrentMode();
        
        state.setCurrentMode(newMode);
        state.setLastSwitchTime(LocalDateTime.now());
        state.setLastSwitchReason(reason);
        
        redisTemplate.opsForValue().set(REDIS_STATE_KEY, state);
        
        // 记录切换日志
        saveSwitchLog(oldMode, newMode, reason);
        
        log.info("策略模式更新: {} -> {}, 原因: {}", oldMode, newMode, reason);
    }

    /**
     * 获取熔断器状态
     */
    public CircuitBreakerStatusDto getCircuitBreakerStatus() {
        Object cached = redisTemplate.opsForValue().get(REDIS_CIRCUIT_KEY);
        if (cached instanceof CircuitBreakerStatusDto) {
            return (CircuitBreakerStatusDto) cached;
        }
        
        // 返回默认关闭状态
        return CircuitBreakerStatusDto.builder()
                .state(CircuitBreakerState.CLOSED)
                .triggered(false)
                .failureCount(0)
                .failureThreshold(3)
                .indicatorFailureCounts(new HashMap<>())
                .build();
    }

    /**
     * 记录指标失败
     */
    public void recordIndicatorFailure(String indicator) {
        CircuitBreakerStatusDto status = getCircuitBreakerStatus();
        
        // 更新失败计数
        Map<String, Integer> failures = status.getIndicatorFailureCounts();
        if (failures == null) {
            failures = new HashMap<>();
        }
        failures.put(indicator, failures.getOrDefault(indicator, 0) + 1);
        status.setIndicatorFailureCounts(failures);
        
        int currentFailures = failures.get(indicator);
        if (currentFailures >= status.getFailureThreshold()) {
            log.warn("指标 {} 连续失败 {} 次，触发熔断", indicator, currentFailures);
            triggerCircuitBreaker(indicator);
        }
        
        redisTemplate.opsForValue().set(REDIS_CIRCUIT_KEY, status);
    }

    /**
     * 触发熔断
     */
    public void triggerCircuitBreaker(String indicator) {
        CircuitBreakerStatusDto status = getCircuitBreakerStatus();
        status.setState(CircuitBreakerState.OPEN);
        status.setTriggered(true);
        status.setTriggerTime(LocalDateTime.now());
        status.setEstimatedRecoverTime(LocalDateTime.now().plusHours(1));
        
        redisTemplate.opsForValue().set(REDIS_CIRCUIT_KEY, status);
        
        log.warn("熔断器触发: 指标={}, 预计恢复时间={}", indicator, status.getEstimatedRecoverTime());
    }

    /**
     * 重置熔断器
     */
    public void resetCircuitBreaker() {
        CircuitBreakerStatusDto status = CircuitBreakerStatusDto.builder()
                .state(CircuitBreakerState.CLOSED)
                .triggered(false)
                .failureCount(0)
                .failureThreshold(3)
                .indicatorFailureCounts(new HashMap<>())
                .build();
        
        redisTemplate.opsForValue().set(REDIS_CIRCUIT_KEY, status);
        log.info("熔断器已重置");
    }

    /**
     * 禁用指标
     */
    public void disableIndicator(String indicator) {
        StrategyStateDto state = getCurrentState();
        List<String> disabled = state.getDisabledIndicators();
        if (disabled == null) {
            disabled = new ArrayList<>();
        }
        if (!disabled.contains(indicator)) {
            disabled.add(indicator);
            state.setDisabledIndicators(disabled);
            redisTemplate.opsForValue().set(REDIS_STATE_KEY, state);
            log.info("指标 {} 已禁用", indicator);
        }
    }

    /**
     * 启用指标
     */
    public void enableIndicator(String indicator) {
        StrategyStateDto state = getCurrentState();
        List<String> disabled = state.getDisabledIndicators();
        if (disabled != null && disabled.contains(indicator)) {
            disabled.remove(indicator);
            state.setDisabledIndicators(disabled);
            redisTemplate.opsForValue().set(REDIS_STATE_KEY, state);
            log.info("指标 {} 已启用", indicator);
        }
    }

    /**
     * 保存切换日志
     */
    private void saveSwitchLog(StrategyMode fromMode, StrategyMode toMode, String reason) {
        SwitchLog log = SwitchLog.builder()
                .logId(java.util.UUID.randomUUID().toString())
                .switchTime(LocalDateTime.now())
                .fromMode(fromMode)
                .toMode(toMode)
                .reason(reason)
                .build();
        
        switchLogRepository.save(log);
    }
}