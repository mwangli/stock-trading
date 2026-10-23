// AI_GENERATE_START-----
package com.stock.job.quartz;

import com.stock.job.service.JobExecutionService;
import com.stock.job.support.InterruptibleQuartzJob;
import com.stock.job.support.QuartzJobSupport;

import com.stock.job.orchestration.DataSyncScheduler;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 股票历史日线低频回补 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@DisallowConcurrentExecution
public class StockPriceHistoryBackfillQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private DataSyncScheduler dataSyncScheduler;

    /**
     * 执行最近三年已确认交易日的历史缺口回补。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("stockPriceHistoryBackfill", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), dataSyncScheduler::backfillHistoricalStockData);
    }
}
// AI_GENERATE_END-----
