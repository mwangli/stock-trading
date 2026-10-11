// AI_GENERATE_START --
package com.stock.tradingExecutor.job;

import com.stock.dataCollector.support.DataSyncScheduler;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 历史数据维护 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class HistoricalDataSyncQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DataSyncScheduler dataSyncScheduler;

    /**
     * 执行历史数据维护。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("historicalDataSync", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), dataSyncScheduler::syncAllHistoricalData);
    }
}
// AI_GENERATE_END --
