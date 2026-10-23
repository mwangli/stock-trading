// AI_GENERATE_START---------
package com.stock.strategy.config;

import com.stock.strategy.entity.StrategyConfig;
import com.stock.strategy.entity.StrategyMode;
import com.stock.strategy.entity.SwitchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 策略模式管理器
 * 管理预设的策略模式和自定义模式
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyModeManager {

    private final StrategyConfigManager configService;
    private final StrategyStateManager stateManager;

    /**
     * 切换到指定模式
     */
    public void switchMode(StrategyMode mode) {
        switchMode(mode, SwitchType.MANUAL, "manual", "user");
    }

    /**
     * 按指定来源切换策略模式。
     *
     * @param mode 目标模式
     * @param switchType 切换类型
     * @param trigger 触发来源
     * @param operator 操作主体
     */
    public void switchMode(StrategyMode mode, SwitchType switchType, String trigger, String operator) {
        String previousVersion = configService.getCurrentConfig().getVersion();
        StrategyConfig config = getModeConfig(mode);
        config.setVersion(nextVersion(previousVersion));
        configService.updateConfig(config);
        stateManager.updateMode(mode, "切换到" + mode.getName() + "模式", switchType, trigger, operator);
        
        log.info("策略模式切换: {}", mode.getName());
    }

    /**
     * 获取模式配置
     */
    public StrategyConfig getModeConfig(StrategyMode mode) {
        StrategyConfig baseConfig = configService.copyCurrentForNewVersion();
        
        switch (mode) {
            case CONSERVATIVE:
                return applyConservativeConfig(baseConfig);
            case AGGRESSIVE:
                return applyAggressiveConfig(baseConfig);
            case BALANCED:
            default:
                return applyBalancedConfig(baseConfig);
        }
    }

    /**
     * 保守模式配置
     * 止损敏感，快速止盈
     */
    private StrategyConfig applyConservativeConfig(StrategyConfig config) {
        config.setMode(StrategyMode.CONSERVATIVE);
        config.setTrailingStopWeight(0.30);
        config.setTrailingStopTolerance(0.015); // 1.5%止损
        config.setRsiWeight(0.25);
        config.setRsiOverboughtThreshold(70.0);
        config.setVolumeWeight(0.25);
        config.setVolumeShrinkThreshold(0.85);
        config.setBollingerWeight(0.20);
        config.setBollingerBreakoutThreshold(1.003);
        config.setHighReturnThreshold(3);
        config.setNormalReturnThreshold(2);
        config.setLowReturnThreshold(1);
        config.setLossReturnThreshold(0);
        return config;
    }

    /**
     * 均衡模式配置
     * 默认配置
     */
    private StrategyConfig applyBalancedConfig(StrategyConfig config) {
        config.setMode(StrategyMode.BALANCED);
        config.setTrailingStopWeight(0.40);
        config.setTrailingStopTolerance(0.02);
        config.setRsiWeight(0.20);
        config.setRsiOverboughtThreshold(75.0);
        config.setVolumeWeight(0.20);
        config.setVolumeShrinkThreshold(0.80);
        config.setBollingerWeight(0.20);
        config.setBollingerBreakoutThreshold(1.005);
        config.setHighReturnThreshold(5);
        config.setNormalReturnThreshold(3);
        config.setLowReturnThreshold(1);
        config.setLossReturnThreshold(0);
        return config;
    }

    /**
     * 激进模式配置
     * 止损宽松，追求更高收益
     */
    private StrategyConfig applyAggressiveConfig(StrategyConfig config) {
        config.setMode(StrategyMode.AGGRESSIVE);
        config.setTrailingStopWeight(0.35);
        config.setTrailingStopTolerance(0.03); // 3%止损
        config.setRsiWeight(0.25);
        config.setRsiOverboughtThreshold(80.0);
        config.setVolumeWeight(0.20);
        config.setVolumeShrinkThreshold(0.75);
        config.setBollingerWeight(0.20);
        config.setBollingerBreakoutThreshold(1.008);
        config.setHighReturnThreshold(8);
        config.setNormalReturnThreshold(5);
        config.setLowReturnThreshold(2);
        config.setLossReturnThreshold(-2);
        return config;
    }

    /**
     * 获取当前模式
     */
    public StrategyMode getCurrentMode() {
        return stateManager.getCurrentState().getCurrentMode();
    }

    private String nextVersion(String version) {
        if (version == null || version.isBlank()) {
            return "1";
        }
        try {
            return String.valueOf(Long.parseLong(version) + 1);
        } catch (NumberFormatException exception) {
            return version + "-switch";
        }
    }
}
// AI_GENERATE_END---------
