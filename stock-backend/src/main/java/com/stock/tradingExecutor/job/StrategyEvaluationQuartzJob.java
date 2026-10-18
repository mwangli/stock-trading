// AI_GENERATE_START ---
package com.stock.tradingExecutor.job;

import com.stock.strategyAgent.job.StrategyEvaluationJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 策略模式评估 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@DisallowConcurrentExecution
public class StrategyEvaluationQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private StrategyEvaluationJob strategyEvaluationJob;

    /**
     * 执行策略模式样本外评估。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("strategy-evaluation", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), strategyEvaluationJob::runEvaluation);
    }
}
// AI_GENERATE_END ---
