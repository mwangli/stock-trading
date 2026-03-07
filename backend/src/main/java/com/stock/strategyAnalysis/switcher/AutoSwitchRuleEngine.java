package com.stock.strategyAnalysis.switcher;

import com.stock.strategyAnalysis.enums.StrategyMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * 自动切换规则引擎
 * 根据预设规则自动切换策略模式
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoSwitchRuleEngine {

    private final StrategySwitcher strategySwitcher;

    /**
     * 根据时段检查是否需要切换
     */
    public void checkTimeBasedRules() {
        LocalTime now = LocalTime.now();

        // 开盘30分钟 (9:30-10:00) -> 激进模式
        if (now.isAfter(LocalTime.of(9, 30)) && now.isBefore(LocalTime.of(10, 0))) {
            strategySwitcher.switchMode(StrategyMode.AGGRESSIVE, "开盘时段，激进模式");
        }
        // 正常交易时段 (10:00-14:30) -> 均衡模式
        else if (now.isAfter(LocalTime.of(10, 0)) && now.isBefore(LocalTime.of(14, 30))) {
            strategySwitcher.switchMode(StrategyMode.BALANCED, "正常交易时段，均衡模式");
        }
        // 尾盘时段 (14:30-14:57) -> 保守模式
        else if (now.isAfter(LocalTime.of(14, 30)) && now.isBefore(LocalTime.of(14, 57))) {
            strategySwitcher.switchMode(StrategyMode.CONSERVATIVE, "尾盘时段，保守模式");
        }
    }

    /**
     * 根据市场状态检查是否需要切换
     */
    public void checkMarketStateRules(double marketChangePercent, double vix) {
        // 市场跌幅检查
        if (marketChangePercent < -5) {
            strategySwitcher.switchMode(StrategyMode.CONSERVATIVE, "大盘跌超5%");
        } else if (marketChangePercent < -3) {
            // 提高卖出阈值10%
            log.info("大盘跌超3%，建议提高卖出阈值");
        } else if (marketChangePercent > 3) {
            strategySwitcher.switchMode(StrategyMode.AGGRESSIVE, "大盘涨幅超3%");
        }

        // VIX波动率检查
        if (vix > 25) {
            strategySwitcher.switchMode(StrategyMode.CONSERVATIVE, "VIX波动率过高");
        }
    }
}