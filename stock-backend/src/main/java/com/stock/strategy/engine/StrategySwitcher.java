// AI_GENERATE_START--------
package com.stock.strategy.engine;

import com.stock.strategy.config.StrategyModeManager;
import com.stock.strategy.config.StrategyStateManager;
import com.stock.strategy.entity.StrategyMode;
import com.stock.strategy.entity.SwitchType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.LocalDate;

/**
 * 策略规则自动切换器。
 * 根据交易时段和市场状态切换预设策略模式，并把触发原因记录到策略状态审计。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategySwitcher {

    private final StrategyModeManager modeManager;
    private final StrategyStateManager stateManager;

    /**
     * 按自动规则切换策略模式。
     *
     * @param newMode 目标策略模式
     * @param reason 触发原因
     */
    private void switchMode(StrategyMode newMode, String reason) {
        log.info("自动切换策略模式: mode={}, reason={}", newMode.getName(), reason);
        modeManager.switchMode(newMode, SwitchType.AUTO, reason, "system-auto");
    }

    /**
     * 根据市场涨跌幅执行自动模式切换。
     *
     * @param marketChangePercent 市场相对前一交易日的涨跌幅，单位为百分比
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
     * 启用指定策略指标。
     *
     * @param indicator 指标标识
     */
    public void enableIndicator(String indicator) {
        stateManager.enableIndicator(indicator);
        log.info("指标 {} 已启用", indicator);
    }

    /**
     * 禁用指定策略指标。
     *
     * @param indicator 指标标识
     */
    public void disableIndicator(String indicator) {
        stateManager.disableIndicator(indicator);
        log.info("指标 {} 已禁用", indicator);
    }


    /**
     * 根据当前交易时段检查并执行策略模式切换。
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
     * 根据市场涨跌幅和波动率检查并执行策略模式切换。
     *
     * @param marketChangePercent 市场涨跌幅，单位为百分比
     * @param vix 波动率指数
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
// AI_GENERATE_END--------
