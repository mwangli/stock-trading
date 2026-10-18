// AI_GENERATE_START ---
package com.stock.strategyAgent.job;

import com.stock.strategyAgent.domain.dto.StrategyEvaluationDto;
import com.stock.strategyAgent.service.StrategyEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 策略模式评估任务入口。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StrategyEvaluationJob {

    private final StrategyEvaluationService evaluationService;

    /**
     * 计算并保存三模式样本外评估结果。
     */
    public void runEvaluation() {
        log.info("策略模式评估任务开始");
        StrategyEvaluationDto result = evaluationService.evaluateAndPersist();
        log.info("策略模式评估任务完成: runId={}, summary={}",
                result.getEvaluationRunId(), result.getSummary());
    }
}
// AI_GENERATE_END ---
