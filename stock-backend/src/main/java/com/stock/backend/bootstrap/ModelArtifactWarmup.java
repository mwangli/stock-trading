// AI_GENERATE_START-----------
package com.stock.backend.bootstrap;

import com.stock.model.config.SentimentTrainingConfig;
import com.stock.model.service.LstmModelRuntimeService;
import com.stock.model.service.SentimentTrainerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 应用就绪后的固定模型制品校验和异步预热服务。
 * 应用先完成 HTTP 启动，再校验固定本地制品目录并加载模型到内存。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelArtifactWarmup {

    private final SentimentTrainingConfig sentimentConfig;
    private final SentimentTrainerService sentimentTrainerService;
    private final LstmModelRuntimeService modelRuntimeService;

    /**
     * 应用就绪后异步校验和加载模型，避免大模型阻塞应用启动。
     */
    @Async("applicationTaskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        warmupSentiment();
        try {
            modelRuntimeService.preloadActiveModel();
        } catch (RuntimeException exception) {
            log.warn("LSTM 模型异步预热失败: {}", exception.getMessage());
        }
    }

    private void warmupSentiment() {
        try {
            if (!sentimentTrainerService.loadModel()) {
                throw new IllegalStateException(sentimentTrainerService.getLastLoadError());
            }
            log.info("Sentiment 模型异步预热完成: model={}, version={}",
                    sentimentConfig.getModelName(), sentimentConfig.getModelVersion());
        } catch (Exception exception) {
            log.warn("Sentiment 模型异步预热失败: model={}, version={}, reason={}",
                    sentimentConfig.getModelName(), sentimentConfig.getModelVersion(), exception.getMessage());
        }
    }
}
// AI_GENERATE_END-----------
