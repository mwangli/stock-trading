// AI_GENERATE_START -
package com.stock.tradingExecutor.job;

import com.stock.strategyAgent.service.AgentApplicationService;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent 每日投研报告 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@DisallowConcurrentExecution
public class AgentDailyReportQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private AgentApplicationService agentApplicationService;

    /**
     * 通过统一任务执行中心生成日报。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("agent-daily-research-report", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), agentApplicationService::runScheduledReport);
    }
}
// AI_GENERATE_END -
