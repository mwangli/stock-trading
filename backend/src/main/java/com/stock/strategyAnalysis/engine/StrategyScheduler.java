package com.stock.strategyAnalysis.engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 策略定时任务调度器
 * 注意: 所有定时任务当前已禁用，如需启用请添加 @Scheduled 注解
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyScheduler {

    private final StockSelector stockSelector;
    private final SignalGenerator signalGenerator;
    private final StrategySwitcher strategySwitcher;

    // 预留方法 - 如需启用请添加 @Scheduled 注解

    /**
     * 综合选股任务
     * 交易日 17:00 执行
     */
//    @Scheduled(cron = "0 0 17 ? * MON-FRI")
    public void runStockSelection() {
        log.info("========== 开始执行选股任务 ==========");
        try {
            stockSelector.selectTopN(10);
            log.info("========== 选股任务完成 ==========");
        } catch (Exception e) {
            log.error("选股任务执行失败", e);
        }
    }

    /**
     * 信号生成任务
     * 交易日 17:30 执行
     */
//    @Scheduled(cron = "0 30 17 ? * MON-FRI")
    public void runSignalGeneration() {
        log.info("========== 开始执行信号生成任务 ==========");
        try {
            signalGenerator.generateSignals();
            log.info("========== 信号生成任务完成 ==========");
        } catch (Exception e) {
            log.error("信号生成任务执行失败", e);
        }
    }

    /**
     * T+1卖出检查任务
     * 交易时段每分钟执行
     */
//    @Scheduled(cron = "0 * 9-11,13-15 ? * MON-FRI")
    public void checkIntradaySell() {
        log.debug("执行T+1卖出检查");
        try {
            // TODO: 从执行模块获取持仓列表
            // intradaySellService.checkAllPositions(positions);
        } catch (Exception e) {
            log.error("T+1卖出检查失败", e);
        }
    }

    /**
     * 尾盘强制卖出检查
     * 14:57 执行
     */
//    @Scheduled(cron = "0 57 14 ? * MON-FRI")
    public void checkForceSell() {
        log.info("========== 检查尾盘强制卖出 ==========");
        try {
            // TODO: 从执行模块获取持仓列表
            // intradaySellService.forceSellAtEnd(stockCodes);
            log.info("========== 尾盘强制卖出检查完成 ==========");
        } catch (Exception e) {
            log.error("尾盘强制卖出检查失败", e);
        }
    }

    /**
     * 时段策略切换检查
     * 每分钟执行
     */
//    @Scheduled(cron = "0 * 9-15 ? * MON-FRI")
    public void checkTimeBasedSwitch() {
        try {
            strategySwitcher.checkTimeBasedRules();
        } catch (Exception e) {
            log.error("时段策略切换检查失败", e);
        }
    }
}
