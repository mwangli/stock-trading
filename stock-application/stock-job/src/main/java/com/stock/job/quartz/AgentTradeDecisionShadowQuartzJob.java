// AI_GENERATE_START------
package com.stock.job.quartz;

import com.stock.job.service.JobExecutionService;
import com.stock.job.support.InterruptibleQuartzJob;
import com.stock.job.support.QuartzJobSupport;

import com.stock.agent.decision.job.AgentTradeDecisionJobCoordinator;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent 交易候选 SHADOW 判断 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@DisallowConcurrentExecution
public class AgentTradeDecisionShadowQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private AgentTradeDecisionJobCoordinator agentTradeDecisionJobCoordinator;

    /**
     * 执行当前候选批次的 SHADOW 判断。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("agentTradeDecisionShadow", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), agentTradeDecisionJobCoordinator::generateCurrentShadowDecisions);
    }
}
// AI_GENERATE_END------
