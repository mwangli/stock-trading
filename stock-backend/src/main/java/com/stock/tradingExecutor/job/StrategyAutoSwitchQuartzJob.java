// AI_GENERATE_START
package com.stock.tradingExecutor.job;

import com.stock.strategyAnalysis.job.StrategyAutoSwitchJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 策略规则自动切换 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@DisallowConcurrentExecution
public class StrategyAutoSwitchQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private StrategyAutoSwitchJob strategyAutoSwitchJob;

    /**
     * 执行市场规则评估和策略模式切换。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("strategy-auto-switch", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), strategyAutoSwitchJob::runAutoSwitch);
    }
}
// AI_GENERATE_END
