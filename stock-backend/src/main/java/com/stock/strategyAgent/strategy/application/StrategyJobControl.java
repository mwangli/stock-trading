// AI_GENERATE_START -
package com.stock.strategyAgent.strategy.application;

/**
 * 智能决策模块对固定策略任务的最小控制端口。
 * 具体 Quartz 和任务配置由 tradingExecutor 提供适配实现。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface StrategyJobControl {

    /**
     * 暂停指定策略任务。
     *
     * @param jobName 固定任务名称
     */
    void pause(String jobName);

    /**
     * 恢复指定策略任务。
     *
     * @param jobName 固定任务名称
     */
    void resume(String jobName);
}
// AI_GENERATE_END -
