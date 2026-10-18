// AI_GENERATE_START --
package com.stock.tradingExecutor.service;

import com.stock.strategyAgent.application.StrategyJobControl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 交易执行模块提供的策略任务控制适配器。
 * 将统一调度中心能力暴露为智能决策模块定义的最小端口。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class StrategyJobControlAdapter implements StrategyJobControl {

    private final JobManagementService jobManagementService;

    /**
     * 暂停指定策略任务。
     *
     * @param jobName 固定任务名称
     */
    @Override
    public void pause(String jobName) {
        jobManagementService.pause(jobName);
    }

    /**
     * 恢复指定策略任务。
     *
     * @param jobName 固定任务名称
     */
    @Override
    public void resume(String jobName) {
        jobManagementService.resume(jobName);
    }
}
// AI_GENERATE_END --
