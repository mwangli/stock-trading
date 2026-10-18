// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

import com.stock.modelService.job.SentimentEvaluationJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 情感模型独立评估 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@DisallowConcurrentExecution
public class SentimentEvaluationQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private SentimentEvaluationJob sentimentEvaluationJob;

    /**
     * 执行情感模型人工评估集验证。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("sentiment-model-evaluation", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), sentimentEvaluationJob::runEvaluation);
    }
}
// AI_GENERATE_END -
