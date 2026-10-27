// AI_GENERATE_START-----------
package com.stock.platform.quartz;

import com.stock.platform.service.JobExecutionService;
import com.stock.platform.support.InterruptibleQuartzJob;
import com.stock.platform.support.QuartzJobSupport;

import com.stock.platform.task.StrategyScheduler;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 模型选股 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class StockSelectionQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private StrategyScheduler strategyScheduler;

    /**
     * 执行模型与情感综合选股。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("stockSelection", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), strategyScheduler::runStockSelection);
    }
}
// AI_GENERATE_END-----------
