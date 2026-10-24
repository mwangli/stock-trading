// AI_GENERATE_START---------
package com.stock.schedule.quartz;

import com.stock.schedule.service.JobExecutionService;
import com.stock.schedule.support.InterruptibleQuartzJob;
import com.stock.schedule.support.QuartzJobSupport;
import com.stock.schedule.task.RealTradingJob;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 真实自动买入 Quartz 任务。
 * 真实交易门禁仍由现有交易服务负责，手动触发不会绕过交易时段、风控与幂等校验。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class RealTradingAutoBuyQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private RealTradingJob realTradingJob;

    /**
     * 执行无人值守候选买入检查。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("realTradingAutoBuy", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), realTradingJob::executeAutoBuys);
    }
}
// AI_GENERATE_END---------
