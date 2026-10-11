// AI_GENERATE_START --
package com.stock.tradingExecutor.job;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 券商五年历史同步 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class BrokerFiveYearHistorySyncQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private BrokerDataSyncJob brokerDataSyncJob;

    /**
     * 执行券商五年历史委托和成交同步。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("broker-five-year-history-sync", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), brokerDataSyncJob::syncLastFiveYears);
    }
}
// AI_GENERATE_END --
