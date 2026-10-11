// AI_GENERATE_START --
package com.stock.tradingExecutor.job;

import com.stock.modelService.job.OfflineLstmTrainingJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * LSTM 离线训练检查 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class LstmOfflineTrainingQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private OfflineLstmTrainingJob offlineLstmTrainingJob;

    /**
     * 执行 LSTM 过期检查和受控训练提交。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("lstmOfflineTraining", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), offlineLstmTrainingJob::trainStaleModels);
    }
}
// AI_GENERATE_END --
