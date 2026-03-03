package com.stock.strategyAnalysis.switcher;

import com.stock.strategyAnalysis.config.StrategyConfigService;
import com.stock.strategyAnalysis.config.StrategyModeManager;
import com.stock.strategyAnalysis.config.StrategyStateManager;
import com.stock.strategyAnalysis.enums.StrategyMode;
import com.stock.strategyAnalysis.enums.SwitchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 策略切换器
 * 处理策略模式的手动和自动切换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategySwitcher {

    private final StrategyModeManager modeManager;
    private final StrategyStateManager stateManager;
    private final StrategyConfigService configService;

    /**
     * 手动切换策略模式
     */
    public void switchMode(StrategyMode newMode, String reason) {
        log.info("手动切换策略模式: {}", newMode.getName());
        modeManager.switchMode(newMode);
    }

    /**
     * 自动切换策略模式
     * 根据市场状态自动调整
     */
    public void autoSwitchByMarket(double marketChangePercent) {
        StrategyMode currentMode = modeManager.getCurrentMode();
        StrategyMode targetMode = currentMode;
        String reason = "";

        // 大盘跌超5% -> 保守模式
        if (marketChangePercent < -5) {
            targetMode = StrategyMode.CONSERVATIVE;
            reason = "大盘跌超5%，自动切换到保守模式";
        }
        // 大盘涨幅超3% -> 激进模式
        else if (marketChangePercent > 3) {
            targetMode = StrategyMode.AGGRESSIVE;
            reason = "大盘涨幅超3%，自动切换到激进模式";
        }

        if (targetMode != currentMode) {
            log.info("自动切换策略: {} -> {}, 原因: {}", currentMode, targetMode, reason);
            modeManager.switchMode(targetMode);
        }
    }

    /**
     * 启用指标
     */
    public void enableIndicator(String indicator) {
        stateManager.enableIndicator(indicator);
        log.info("指标 {} 已启用", indicator);
    }

    /**
     * 禁用指标
     */
    public void disableIndicator(String indicator) {
        stateManager.disableIndicator(indicator);
        log.info("指标 {} 已禁用", indicator);
    }

    /**
     * 调整指标权重
     */
    public void adjustIndicatorWeight(String indicator, double weight) {
        log.info("调整指标 {} 权重为 {}", indicator, weight);
        // TODO: 实现权重调整逻辑
    }
}