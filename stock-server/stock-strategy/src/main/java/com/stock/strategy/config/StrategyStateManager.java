// AI_GENERATE_START-----------------
package com.stock.strategy.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.strategy.dto.StrategyStateDto;
import com.stock.strategy.entity.StrategyMode;
import com.stock.strategy.entity.StrategyRuntimeState;
import com.stock.strategy.entity.SwitchLog;
import com.stock.strategy.entity.SwitchType;
import com.stock.strategy.repository.StrategyRuntimeStateRepository;
import com.stock.strategy.repository.SwitchLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 策略状态管理器。
 * 使用 MySQL 保存策略开关、模式和指标状态，支持多实例共享与重启恢复。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyStateManager {

    private static final String STATE_KEY = "strategy:state";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() { };

    private final StrategyRuntimeStateRepository stateRepository;
    private final SwitchLogRepository switchLogRepository;
    private final StrategyConfigManager configService;
    private final ObjectMapper objectMapper;

    /**
     * 获取当前策略状态。
     *
     * @return 数据库中的当前策略状态
     */
    public StrategyStateDto getCurrentState() {
        StrategyRuntimeState state = stateRepository.findById(STATE_KEY)
                .orElseGet(this::createDefaultState);
        return toDto(state);
    }

    /**
     * 设置整体策略是否启用。
     *
     * @param enabled true 启用，false 禁用
     */
    public void setEnabled(boolean enabled) {
        StrategyRuntimeState state = loadForUpdate();
        state.setEnabled(enabled);
        state.setLastSwitchTime(LocalDateTime.now());
        state.setLastSwitchReason(enabled ? "用户启用" : "用户禁用");
        saveState(state);
        log.info("策略总开关已{}", enabled ? "启用" : "禁用");
    }

    /**
     * 更新策略模式并记录切换日志。
     *
     * @param newMode 新策略模式
     * @param reason 切换原因
     */
    public void updateMode(StrategyMode newMode, String reason) {
        updateMode(newMode, reason, SwitchType.MANUAL, "manual", "user");
    }

    /**
     * 更新策略模式并记录完整的切换审计信息。
     *
     * @param newMode 新策略模式
     * @param reason 切换原因
     * @param switchType 切换类型
     * @param trigger 触发来源
     * @param operator 操作主体
     */
    public void updateMode(StrategyMode newMode, String reason, SwitchType switchType,
                           String trigger, String operator) {
        StrategyRuntimeState state = loadForUpdate();
        StrategyMode oldMode = parseMode(state.getCurrentMode());
        if (oldMode == newMode) {
            saveState(state);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        state.setCurrentMode(newMode.name());
        state.setLastSwitchTime(now);
        state.setLastSwitchReason(reason);
        if (switchType == SwitchType.AUTO) {
            state.setLastAutoSwitchTime(now);
        }
        saveState(state);
        saveSwitchLog(oldMode, newMode, reason, switchType, trigger, operator);
        log.info("策略模式更新: {} -> {}, 原因: {}", oldMode, newMode, reason);
    }

    /**
     * 设置规则自动切换开关。
     *
     * @param enabled 是否启用
     */
    public void setAutoSwitchEnabled(boolean enabled) {
        StrategyRuntimeState state = loadForUpdate();
        state.setAutoSwitchEnabled(enabled);
        saveState(state);
        log.info("策略规则自动切换已{}", enabled ? "启用" : "禁用");
    }

    /**
     * 判断规则自动切换是否可执行。
     *
     * @param cooldownHours 自动切换冷却小时数
     * @return 是否允许执行
     */
    public boolean canAutoSwitch(long cooldownHours) {
        StrategyStateDto state = getCurrentState();
        if (!state.isAutoSwitchEnabled()) {
            return false;
        }
        LocalDateTime lastSwitchTime = state.getLastSwitchTime();
        return lastSwitchTime == null || lastSwitchTime.plusHours(cooldownHours).isBefore(LocalDateTime.now());
    }

    /**
     * 禁用指定策略指标。
     *
     * @param indicator 指标标识
     */
    public void disableIndicator(String indicator) {
        StrategyRuntimeState state = loadForUpdate();
        List<String> disabled = readDisabledIndicators(state);
        if (!disabled.contains(indicator)) {
            disabled.add(indicator);
            state.setDisabledIndicatorsJson(writeDisabledIndicators(disabled));
            saveState(state);
            log.info("指标 {} 已禁用", indicator);
        }
    }

    /**
     * 启用指定策略指标。
     *
     * @param indicator 指标标识
     */
    public void enableIndicator(String indicator) {
        StrategyRuntimeState state = loadForUpdate();
        List<String> disabled = readDisabledIndicators(state);
        if (disabled.remove(indicator)) {
            state.setDisabledIndicatorsJson(writeDisabledIndicators(disabled));
            saveState(state);
            log.info("指标 {} 已启用", indicator);
        }
    }

    private StrategyRuntimeState loadForUpdate() {
        return stateRepository.findByStateKey(STATE_KEY).orElseGet(this::createDefaultState);
    }

    private StrategyRuntimeState createDefaultState() {
        StrategyRuntimeState state = StrategyRuntimeState.builder()
                .stateKey(STATE_KEY)
                .enabled(true)
                .currentMode(StrategyMode.BALANCED.name())
                .disabledIndicatorsJson("[]")
                .lastSwitchReason("初始化")
                .configVersion(currentConfigVersion())
                .autoSwitchEnabled(false)
                .updatedAt(LocalDateTime.now())
                .build();
        return stateRepository.save(state);
    }

    private void saveState(StrategyRuntimeState state) {
        state.setConfigVersion(currentConfigVersion());
        state.setUpdatedAt(LocalDateTime.now());
        stateRepository.save(state);
    }

    private String currentConfigVersion() {
        String version = configService.getCurrentConfig().getVersion();
        return version == null || version.isBlank() ? "1" : version;
    }

    private StrategyStateDto toDto(StrategyRuntimeState state) {
        return StrategyStateDto.builder()
                .enabled(state.isEnabled())
                .currentMode(parseMode(state.getCurrentMode()))
                .disabledIndicators(readDisabledIndicators(state))
                .lastSwitchTime(state.getLastSwitchTime())
                .lastSwitchReason(state.getLastSwitchReason())
                .configVersion(state.getConfigVersion())
                .autoSwitchEnabled(state.isAutoSwitchEnabled())
                .lastAutoSwitchTime(state.getLastAutoSwitchTime())
                .build();
    }

    private StrategyMode parseMode(String mode) {
        try {
            return StrategyMode.valueOf(mode);
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.warn("策略运行状态中的模式无效，按 BALANCED 处理: {}", mode);
            return StrategyMode.BALANCED;
        }
    }

    private List<String> readDisabledIndicators(StrategyRuntimeState state) {
        try {
            String json = state.getDisabledIndicatorsJson();
            return json == null || json.isBlank()
                    ? new ArrayList<>()
                    : new ArrayList<>(objectMapper.readValue(json, STRING_LIST_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("策略禁用指标状态无法解析", exception);
        }
    }

    private String writeDisabledIndicators(List<String> indicators) {
        try {
            return objectMapper.writeValueAsString(indicators);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("策略禁用指标状态无法保存", exception);
        }
    }

    private void saveSwitchLog(StrategyMode fromMode, StrategyMode toMode, String reason,
                               SwitchType switchType, String trigger, String operator) {
        SwitchLog switchLog = SwitchLog.builder()
                .logId(java.util.UUID.randomUUID().toString())
                .switchTime(LocalDateTime.now())
                .switchType(switchType)
                .fromMode(fromMode)
                .toMode(toMode)
                .reason(reason)
                .trigger(trigger)
                .operator(operator)
                .build();
        switchLogRepository.save(switchLog);
    }
}
// AI_GENERATE_END-----------------
