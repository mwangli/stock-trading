// AI_GENERATE_START -
package com.stock.strategyAgent.application;

import java.util.Optional;

/**
 * 智能决策模块读取任务运行摘要的端口。
 * 具体调度记录由 tradingExecutor 提供适配实现，避免前置模块反向依赖调度实现。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface JobExecutionQuery {

    /**
     * 查询指定任务最近一次运行摘要。
     *
     * @param jobName 固定任务名称
     * @return 最近一次运行摘要，没有记录时返回空
     */
    Optional<JobExecutionSummary> getLatestRun(String jobName);
}
// AI_GENERATE_END -
