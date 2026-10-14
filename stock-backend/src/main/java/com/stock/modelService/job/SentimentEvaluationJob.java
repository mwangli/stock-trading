// AI_GENERATE_START -
package com.stock.modelService.job;

import com.stock.modelService.domain.dto.SentimentEvaluationDto;
import com.stock.modelService.service.SentimentEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 情感模型人工评估任务入口。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentEvaluationJob {

    private final SentimentEvaluationService evaluationService;

    /**
     * 执行人工标注集独立评估。
     */
    public void runEvaluation() {
        log.info("情感模型独立评估任务开始");
        SentimentEvaluationDto result = evaluationService.evaluateAndPersist();
        log.info("情感模型独立评估任务完成: runId={}, status={}, conclusion={}",
                result.getEvaluationRunId(), result.getStatus(), result.getConclusion());
    }
}
// AI_GENERATE_END -
