// AI_GENERATE_START ---
package com.stock.strategyAnalysis.config;

import com.stock.strategyAnalysis.domain.dto.StrategyStateDto;
import com.stock.strategyAnalysis.domain.entity.StrategyMode;
import com.stock.strategyAnalysis.domain.entity.SwitchLog;
import com.stock.strategyAnalysis.persistence.SwitchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 策略状态管理器。
 * 管理单实例运行时的策略开关、策略模式和指标启停状态。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyStateManager {

    private static final String STATE_KEY = "strategy:state";
    /**
     * 使用本地内存保存单实例运行所需的策略状态
     */
    private final ConcurrentMap<String, Object> stateStore = new ConcurrentHashMap<>();

    private final SwitchLogRepository switchLogRepository;

    /**
     * 获取当前策略状态。
     *
     * @return 当前策略状态；未初始化时返回默认状态
     */
    public StrategyStateDto getCurrentState() {
        Object cached = stateStore.get(STATE_KEY);
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
     * 设置整体策略是否启用（选股/策略总开关）
     *
     * @param enabled true 启用，false 禁用
     */
    public void setEnabled(boolean enabled) {
        StrategyStateDto state = getCurrentState();
        state.setEnabled(enabled);
        state.setLastSwitchTime(LocalDateTime.now());
        state.setLastSwitchReason(enabled ? "用户启用" : "用户禁用");
        stateStore.put(STATE_KEY, state);
        log.info("策略总开关已{}", enabled ? "启用" : "禁用");
    }

    /**
     * 更新策略模式并记录切换日志。
     *
     * @param newMode 新策略模式
     * @param reason 切换原因
     */
    public void updateMode(StrategyMode newMode, String reason) {
        StrategyStateDto state = getCurrentState();
        StrategyMode oldMode = state.getCurrentMode();
        
        state.setCurrentMode(newMode);
        state.setLastSwitchTime(LocalDateTime.now());
        state.setLastSwitchReason(reason);
        stateStore.put(STATE_KEY, state);
        
        // 记录切换日志
        saveSwitchLog(oldMode, newMode, reason);
        
        log.info("策略模式更新: {} -> {}, 原因: {}", oldMode, newMode, reason);
    }

    /**
     * 禁用指定策略指标。
     *
     * @param indicator 指标标识
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
            stateStore.put(STATE_KEY, state);
            log.info("指标 {} 已禁用", indicator);
        }
    }

    /**
     * 启用指定策略指标。
     *
     * @param indicator 指标标识
     */
    public void enableIndicator(String indicator) {
        StrategyStateDto state = getCurrentState();
        List<String> disabled = state.getDisabledIndicators();
        if (disabled != null && disabled.contains(indicator)) {
            disabled.remove(indicator);
            state.setDisabledIndicators(disabled);
            stateStore.put(STATE_KEY, state);
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
// AI_GENERATE_END ---
