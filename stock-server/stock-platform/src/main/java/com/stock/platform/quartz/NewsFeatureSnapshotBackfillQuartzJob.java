// AI_GENERATE_START- -
package com.stock.platform.quartz;

import com.stock.platform.service.JobExecutionService;
import com.stock.platform.support.InterruptibleQuartzJob;
import com.stock.platform.support.QuartzJobSupport;
import com.stock.platform.task.DataSyncScheduler;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 新闻日级特征快照历史回填 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@DisallowConcurrentExecution
public class NewsFeatureSnapshotBackfillQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DataSyncScheduler dataSyncScheduler;

    /**
     * 执行 `temporal-news-v1` 分批回填与完成态审计。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("newsFeatureSnapshotBackfill", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), dataSyncScheduler::backfillNewsFeatureSnapshots);
    }
}
// AI_GENERATE_END- -
