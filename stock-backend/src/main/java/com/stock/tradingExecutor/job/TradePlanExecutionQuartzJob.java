// AI_GENERATE_START --
package com.stock.tradingExecutor.job;

import com.stock.tradingExecutor.service.TradePlanExecutionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent TradePlan 自动执行 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@DisallowConcurrentExecution
public class TradePlanExecutionQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private TradePlanExecutionService tradePlanExecutionService;

    /**
     * 执行待复核交易计划。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("agent-trade-plan-execution", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), tradePlanExecutionService::executePendingPlans);
    }
}
// AI_GENERATE_END --
