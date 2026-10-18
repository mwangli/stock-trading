// AI_GENERATE_START --
package com.stock.strategyAgent.engine;

import com.stock.strategyAgent.config.StrategyConfigService;
import com.stock.strategyAgent.config.StrategyModeManager;
import com.stock.strategyAgent.config.StrategyStateManager;
import com.stock.strategyAgent.domain.entity.StrategyMode;
import com.stock.strategyAgent.domain.entity.SwitchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.LocalDate;

/**
 * 策略切换器
 * 处理策略模式的手动和自动切换，以及按时段/市场状态的规则切换
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
        autoSwitchByMarket(marketChangePercent, null);
    }

    /**
     * 根据市场涨跌幅和数据日期执行规则自动切换。
     *
     * @param marketChangePercent 市场相对前一交易日的涨跌幅，单位为百分比
     * @param marketDate 市场数据所属交易日
     */
    public void autoSwitchByMarket(double marketChangePercent, LocalDate marketDate) {
        if (!Double.isFinite(marketChangePercent)) {
            log.warn("市场涨跌幅无效，跳过自动切换: {}", marketChangePercent);
            return;
        }
        if (!stateManager.canAutoSwitch(24)) {
            log.info("自动切换未执行：开关未启用或仍处于冷却期");
            return;
        }
        StrategyMode currentMode = modeManager.getCurrentMode();
        StrategyMode targetMode = currentMode;
        String reason;

        // 大盘跌超5% -> 保守模式
        if (marketChangePercent <= -5) {
            targetMode = StrategyMode.CONSERVATIVE;
            reason = "大盘跌超5%，自动切换到保守模式";
        }
        // 大盘涨幅超3% -> 激进模式
        else if (marketChangePercent >= 3) {
            targetMode = StrategyMode.AGGRESSIVE;
            reason = "大盘涨幅超3%，自动切换到激进模式";
        } else {
            targetMode = StrategyMode.BALANCED;
            reason = "市场波动回到常态，自动切换到均衡模式";
        }

        if (targetMode != currentMode) {
            log.info("自动切换策略: {} -> {}, 原因: {}", currentMode, targetMode, reason);
            String trigger = marketDate == null ? "market-rule" : "market-rule:" + marketDate;
            modeManager.switchMode(targetMode, SwitchType.AUTO, trigger, "system-auto");
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

    /**
     * 根据时段检查是否需要切换策略模式
     * 开盘30分钟激进、正常时段均衡、尾盘保守
     */
    public void checkTimeBasedRules() {
        LocalTime now = LocalTime.now();

        if (now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(10, 0))) {
            switchMode(StrategyMode.AGGRESSIVE, "开盘时段，激进模式");
        } else if (now.isAfter(LocalTime.of(10, 0)) && now.isBefore(LocalTime.of(14, 30))) {
            switchMode(StrategyMode.BALANCED, "正常交易时段，均衡模式");
        } else if (now.isAfter(LocalTime.of(14, 30)) && now.isBefore(LocalTime.of(14, 57))) {
            switchMode(StrategyMode.CONSERVATIVE, "尾盘时段，保守模式");
        }
    }

    /**
     * 根据市场状态检查是否需要切换策略模式
     * 大盘涨跌幅、VIX 波动率
     */
    public void checkMarketStateRules(double marketChangePercent, double vix) {
        if (marketChangePercent < -5) {
            switchMode(StrategyMode.CONSERVATIVE, "大盘跌超5%");
        } else if (marketChangePercent < -3) {
            log.info("大盘跌超3%，建议提高卖出阈值");
        } else if (marketChangePercent > 3) {
            switchMode(StrategyMode.AGGRESSIVE, "大盘涨幅超3%");
        }

        if (vix > 25) {
            switchMode(StrategyMode.CONSERVATIVE, "VIX波动率过高");
        }
    }
}
// AI_GENERATE_END --
