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
 * Agent SHADOW 判断市场结果评估 Quartz 任务。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@DisallowConcurrentExecution
public class AgentTradeDecisionEvaluationQuartzJob extends InterruptibleQuartzJob {

    @Autowired
    private JobExecutionService jobExecutionService;

    @Autowired
    private AgentTradeDecisionJobCoordinator agentTradeDecisionJobCoordinator;

    /**
     * 执行待评估 SHADOW 判断的市场结果回写。
     *
     * @param context Quartz 执行上下文
     */
    @Override
    protected void executeInterruptibly(JobExecutionContext context) {
        jobExecutionService.execute("agentTradeDecisionEvaluation", QuartzJobSupport.triggerSource(context),
                QuartzJobSupport.triggeredBy(context), QuartzJobSupport.runId(context),
                context.getScheduledFireTime(), agentTradeDecisionJobCoordinator::evaluatePendingShadowDecisions);
    }
}
// AI_GENERATE_END------
