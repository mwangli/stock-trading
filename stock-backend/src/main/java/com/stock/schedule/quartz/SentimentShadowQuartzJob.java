// AI_GENERATE_START --
package com.stock.schedule.quartz;

import com.stock.schedule.service.JobExecutionService;
import com.stock.schedule.support.InterruptibleQuartzJob;
import com.stock.schedule.support.QuartzJobSupport;
import com.stock.model.service.SentimentShadowDeploymentService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Mengzi 情感模型影子推理和自动路由 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@DisallowConcurrentExecution
public class SentimentShadowQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private SentimentShadowDeploymentService shadowDeploymentService;

    /**
     * 执行 Mengzi 顺序影子推理、后验评估和自动路由检查。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("sentimentMengziShadow", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), shadowDeploymentService::runShadowCycle);
    }
}
// AI_GENERATE_END --
