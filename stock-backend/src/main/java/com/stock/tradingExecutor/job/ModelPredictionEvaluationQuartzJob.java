// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

import com.stock.modelService.job.DailyModelPredictionJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 模型预测实际结果自动评估 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@DisallowConcurrentExecution
public class ModelPredictionEvaluationQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DailyModelPredictionJob dailyModelPredictionJob;

    /**
     * 扫描并刷新所有待评估预测批次。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("modelPredictionEvaluation", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), dailyModelPredictionJob::refreshPredictionEvaluations);
    }
}
// AI_GENERATE_END -
