package com.stock.strategyAnalysis.config;

import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.domain.entity.StrategyMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 策略模式管理器
 * 管理预设的策略模式和自定义模式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyModeManager {

    private final StrategyConfigService configService;
    private final StrategyStateManager stateManager;

    /**
     * 切换到指定模式
     */
    public void switchMode(StrategyMode mode) {
        StrategyConfig config = getModeConfig(mode);
        configService.updateConfig(config);
        stateManager.updateMode(mode, "切换到" + mode.getName() + "模式");
        
        log.info("策略模式切换: {}", mode.getName());
    }

    /**
     * 获取模式配置
     */
    public StrategyConfig getModeConfig(StrategyMode mode) {
        StrategyConfig baseConfig = configService.getCurrentConfig();
        
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
        config.setHighReturnThreshold(45);
        config.setNormalReturnThreshold(55);
        config.setLowReturnThreshold(65);
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
        config.setHighReturnThreshold(50);
        config.setNormalReturnThreshold(60);
        config.setLowReturnThreshold(70);
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
        config.setHighReturnThreshold(60);
        config.setNormalReturnThreshold(70);
        config.setLowReturnThreshold(80);
        return config;
    }

    /**
     * 获取当前模式
     */
    public StrategyMode getCurrentMode() {
        return stateManager.getCurrentState().getCurrentMode();
    }
}