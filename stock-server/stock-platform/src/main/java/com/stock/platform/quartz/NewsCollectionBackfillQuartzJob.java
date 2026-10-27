// AI_GENERATE_START---
package com.stock.platform.quartz;

import com.stock.platform.task.DataSyncScheduler;
import com.stock.platform.service.JobExecutionService;
import com.stock.platform.support.InterruptibleQuartzJob;
import com.stock.platform.support.QuartzJobSupport;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 新闻采集治理历史回填 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@DisallowConcurrentExecution
public class NewsCollectionBackfillQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DataSyncScheduler dataSyncScheduler;

    /**
     * 执行新闻采集治理历史回填。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("newsCollectionBackfill", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), dataSyncScheduler::backfillNewsCollectionQuality);
    }
}
// AI_GENERATE_END---
