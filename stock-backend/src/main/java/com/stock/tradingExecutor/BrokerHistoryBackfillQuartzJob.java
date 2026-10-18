// AI_GENERATE_START- --
package com.stock.tradingExecutor;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 券商三年历史回补 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class BrokerHistoryBackfillQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private BrokerDataSyncJob brokerDataSyncJob;

    /**
     * 执行券商三年历史委托和成交回补。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("broker-history-backfill", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), brokerDataSyncJob::syncHistoryBackfill);
    }
}
// AI_GENERATE_END --
