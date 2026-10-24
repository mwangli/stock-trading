// AI_GENERATE_START--------
package com.stock.schedule.quartz;

import com.stock.schedule.service.JobExecutionService;
import com.stock.schedule.support.InterruptibleQuartzJob;
import com.stock.schedule.support.QuartzJobSupport;

import com.stock.schedule.task.DailyModelPredictionJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 每日模型预测与评估补缺 Quartz 任务。
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
     * 执行每日预测补缺、预测队列消费和评估回填。
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
// AI_GENERATE_END--------
