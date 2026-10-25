// AI_GENERATE_START --
package com.stock.schedule.quartz;

import com.stock.platform.service.GlobalDataCleanupService;
import com.stock.schedule.service.JobExecutionService;
import com.stock.schedule.support.InterruptibleQuartzJob;
import com.stock.schedule.support.QuartzJobSupport;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 全局业务数据月度清理 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@DisallowConcurrentExecution
public class GlobalDataCleanupQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private GlobalDataCleanupService globalDataCleanupService;

    /**
     * 执行全局业务数据清理并记录统一任务运行状态。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("globalDataCleanup", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), globalDataCleanupService::cleanupAll);
    }
}
// AI_GENERATE_END --
