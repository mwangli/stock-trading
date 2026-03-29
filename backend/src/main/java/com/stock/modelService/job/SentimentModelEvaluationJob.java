package com.stock.modelService.job;

import com.stock.modelService.config.SentimentEvaluationConfig;
import com.stock.modelService.persistence.SentimentAutoLabelRepository;
import com.stock.modelService.service.ModelEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 情感模型评估任务执行入口
 * <p>
 * 说明：
 * <ul>
 *     <li>本类不直接使用 @Scheduled 注解；</li>
 *     <li>由统一调度中心 {@link com.stock.tradingExecutor.job.JobSchedulerService}
 *     通过 JobConfig 中配置的 beanName + methodName 进行反射调用；</li>
 *     <li>默认任务配置见 {@link com.stock.tradingExecutor.job.JobBootstrap} 中的 sentimentModelEvaluation 任务。</li>
 * </ul>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentModelEvaluationJob {

    private final ModelEvaluationService modelEvaluationService;
    private final SentimentEvaluationConfig modelEvaluationConfig;
    private final SentimentAutoLabelRepository sentimentAutoLabelRepository;

    /**
     * 执行情感模型评估任务
     * 每日收盘后（16:30）执行
     */
    public void evaluateSentimentModel() {
        try {
            log.info("[SentimentModelEvaluationJob] 开始执行情感模型评估任务");

            ModelEvaluationService.EvaluationResult result = modelEvaluationService.evaluateModel("scheduled");

            log.info("[SentimentModelEvaluationJob] 评估完成: 准确率={}, F1={}, 夏普比率={}, 阈值状态={}",
                    result.getAccuracy(), result.getF1Score(), result.getSharpeRatio(), result.getThresholdStatus());

            if (result.isShouldFineTune()) {
                long availableLabels = sentimentAutoLabelRepository.countByStatus("validated");

                if (availableLabels >= modelEvaluationConfig.getMinSamplesForFineTune()) {
                    log.warn("[SentimentModelEvaluationJob] ⚠️ 微调触发条件已满足，建议进行模型微调！准确率={}, F1={}, 可用标注样本={}",
                            result.getAccuracy(), result.getF1Score(), availableLabels);
                } else {
                    log.warn("[SentimentModelEvaluationJob] ⚠️ 微调触发但标注样本不足: 需要{}，当前{}",
                            modelEvaluationConfig.getMinSamplesForFineTune(), availableLabels);
                }
            } else {
                log.info("[SentimentModelEvaluationJob] 模型状态正常，无需微调");
            }

        } catch (Exception e) {
            log.error("[SentimentModelEvaluationJob] 情感模型评估任务执行异常", e);
        }
    }
}