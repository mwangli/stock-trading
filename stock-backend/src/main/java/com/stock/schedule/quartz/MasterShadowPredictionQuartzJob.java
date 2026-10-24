// AI_GENERATE_START --
package com.stock.schedule.quartz;

import com.stock.schedule.service.JobExecutionService;
import com.stock.schedule.support.InterruptibleQuartzJob;
import com.stock.schedule.support.QuartzJobSupport;
import com.stock.model.service.MasterShadowPredictionService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MASTER 时序模型影子预测和动态权重刷新 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@DisallowConcurrentExecution
public class MasterShadowPredictionQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private MasterShadowPredictionService shadowPredictionService;

    /**
     * 对最近生产预测批次执行 MASTER 影子预测和后验评估。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("masterShadowPrediction", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), shadowPredictionService::runShadowCycle);
    }
}
// AI_GENERATE_END --
