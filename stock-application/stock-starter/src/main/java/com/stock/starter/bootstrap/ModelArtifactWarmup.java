// AI_GENERATE_START----------
package com.stock.starter.bootstrap;

import com.stock.model.config.SentimentTrainingConfig;
import com.stock.model.service.LstmModelRuntimeService;
import com.stock.model.service.SentimentTrainerService;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.rag.DjlEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

    private final AgentProperties agentProperties;
    private final SentimentTrainingConfig sentimentConfig;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final SentimentTrainerService sentimentTrainerService;
    private final LstmModelRuntimeService modelRuntimeService;

    /**
     * 应用就绪后异步校验和加载模型，避免大模型阻塞应用启动。
     */
    @Async("applicationTaskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        warmupEmbedding();
        warmupSentiment();
        try {
            modelRuntimeService.preloadActiveModel();
        } catch (RuntimeException exception) {
            log.warn("LSTM 模型异步预热失败: {}", exception.getMessage());
        }
    }

    private void warmupEmbedding() {
        AgentProperties.EmbeddingProperties embedding = agentProperties.getEmbedding();
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        DjlEmbeddingModel model = embeddingModel instanceof DjlEmbeddingModel djlModel ? djlModel : null;
        if (model == null || !embedding.isEnabled() || !"djl".equalsIgnoreCase(embedding.getProvider())) {
            return;
        }
        try {
            model.ensureReady();
            log.info("Embedding 模型异步预热完成: model={}, version={}",
                    embedding.getModel(), embedding.getModelVersion());
        } catch (Exception exception) {
            log.warn("Embedding 模型异步预热失败: model={}, version={}, reason={}",
                    embedding.getModel(), embedding.getModelVersion(), exception.getMessage());
            log.warn("Embedding 模型异步预热完整异常链", exception);
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
// AI_GENERATE_END----------
