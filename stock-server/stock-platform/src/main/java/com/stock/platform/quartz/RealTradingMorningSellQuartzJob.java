// AI_GENERATE_START -
package com.stock.platform.quartz;

import com.stock.platform.service.JobExecutionService;
import com.stock.platform.support.InterruptibleQuartzJob;
import com.stock.platform.support.QuartzJobSupport;
import com.stock.platform.task.RealTradingJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 上午分阶段卖出 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@DisallowConcurrentExecution
public class RealTradingMorningSellQuartzJob extends InterruptibleQuartzJob {
    @Autowired
    private JobExecutionService jobExecutionService;
    @Autowired
    private RealTradingJob realTradingJob;

    /** 执行上午分阶段卖出。 */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("realTradingMorningSell", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), realTradingJob::executeMorningSell);
    }
}
// AI_GENERATE_END -
