// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

import com.stock.modelService.job.DailyModelPredictionJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 每日全市场模型预测 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@DisallowConcurrentExecution
public class DailyModelPredictionQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DailyModelPredictionJob dailyModelPredictionJob;

    /**
     * 执行每日全市场模型预测。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("dailyModelPrediction", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), dailyModelPredictionJob::runDailyPrediction);
    }
}
// AI_GENERATE_END -
