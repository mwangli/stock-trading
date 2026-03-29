package com.stock.modelService.job;

import com.stock.modelService.service.AutoLabelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 情感模型自动标注任务执行入口
 * <p>
 * 说明：
 * <ul>
 *     <li>本类不直接使用 @Scheduled 注解；</li>
 *     <li>由统一调度中心 {@link com.stock.tradingExecutor.job.JobSchedulerService}
 *     通过 JobConfig 中配置的 beanName + methodName 进行反射调用；</li>
 *     <li>默认任务配置见 {@link com.stock.tradingExecutor.job.JobBootstrap} 中的 sentimentAutoLabel 任务。</li>
 * </ul>
 *
 * @author mwangli
 * @since 2026-03-29
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SentimentAutoLabelJob {

    private final AutoLabelService autoLabelService;

    /**
     * 执行自动标注任务
     * 每日收盘后（17:00）执行
     */
    public void generateAutoLabels() {
        log.info("[SentimentAutoLabelJob] 开始执行自动标注任务");
        try {
            int labelCount = autoLabelService.generateLabels();
            log.info("[SentimentAutoLabelJob] 自动标注任务完成，共生成 {} 条标注", labelCount);
            if (labelCount < 100) {
                log.warn("[SentimentAutoLabelJob] 可用标注数量可能不足，当前: {}", labelCount);
            }
        } catch (Exception e) {
            log.error("[SentimentAutoLabelJob] 自动标注任务执行异常", e);
        }
    }
}