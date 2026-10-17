// AI_GENERATE_START ---
package com.stock.tradingExecutor.job;

import com.stock.strategyAgent.strategy.job.DailyBaselineBacktestJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 日线基准回测 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class DailyBaselineBacktestQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DailyBaselineBacktestJob dailyBaselineBacktestJob;

    /**
     * 执行最近五年动量基准回测。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("daily-five-year-baseline-backtest",
                QuartzJobSupport.triggerSource(context), QuartzJobSupport.triggeredBy(context),
                QuartzJobSupport.runId(context), context.getScheduledFireTime(),
                dailyBaselineBacktestJob::runFiveYearMomentumBaseline);
    }
}
// AI_GENERATE_END ---
