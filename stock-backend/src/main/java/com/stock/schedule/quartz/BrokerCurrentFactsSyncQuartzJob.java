// AI_GENERATE_START----------
package com.stock.schedule.quartz;

import com.stock.schedule.service.JobExecutionService;
import com.stock.schedule.support.InterruptibleQuartzJob;
import com.stock.schedule.support.QuartzJobSupport;
import com.stock.schedule.task.BrokerDataSyncJob;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 券商当前事实同步 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class BrokerCurrentFactsSyncQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private BrokerDataSyncJob brokerDataSyncJob;

    /**
     * 执行券商账户、持仓、委托和成交只读同步。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("broker-current-facts-sync", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), brokerDataSyncJob::syncCurrentFacts);
    }
}
// AI_GENERATE_END----------
