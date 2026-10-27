// AI_GENERATE_START---------
package com.stock.platform.quartz;

import com.stock.platform.service.JobExecutionService;
import com.stock.platform.support.InterruptibleQuartzJob;
import com.stock.platform.support.QuartzJobSupport;

import com.stock.platform.task.RealTradingJob;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 真实 T+1 退出 Quartz 任务。
 * 真实交易门禁仍由现有交易服务负责，手动触发不会绕过交易时段、风控与幂等校验。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@DisallowConcurrentExecution
public class RealTradingT1ExitQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private RealTradingJob realTradingJob;

    /**
     * 执行无人值守 T+1 退出检查。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("realTradingT1Exit", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), realTradingJob::checkT1Exits);
    }
}
// AI_GENERATE_END---------
