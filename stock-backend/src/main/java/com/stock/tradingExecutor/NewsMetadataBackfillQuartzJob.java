// AI_GENERATE_START
package com.stock.tradingExecutor;

import com.stock.dataCollector.support.DataSyncScheduler;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 历史新闻元数据补全 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@DisallowConcurrentExecution
public class NewsMetadataBackfillQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DataSyncScheduler dataSyncScheduler;

    /**
     * 执行历史新闻事件类型和信息优先级补全。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("newsMetadataBackfill", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), dataSyncScheduler::backfillNewsMetadata);
    }
}
// AI_GENERATE_END
