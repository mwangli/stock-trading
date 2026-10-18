// AI_GENERATE_START --
package com.stock.modelService.service;

import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.strategyAgent.config.AgentProperties;
import com.stock.strategyAgent.rag.DjlEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 应用就绪后的模型制品初始化和异步预热服务。
 * 应用先完成 HTTP 启动，再从模型托管平台初始化缺失制品，最后从 GridFS 加载模型到内存。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelArtifactWarmupService {

    private final ModelArtifactGridFsService artifactService;
    private final AgentProperties agentProperties;
    private final SentimentTrainingConfig sentimentConfig;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final SentimentTrainerService sentimentTrainerService;
    private final LstmTrainerService lstmTrainerService;

    /**
     * 应用就绪后异步初始化和加载模型，避免大模型阻塞应用启动。
     */
    @Async("applicationTaskExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        warmupEmbedding();
        warmupSentiment();
        try {
            lstmTrainerService.preloadActiveModel();
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
            if (!artifactService.exists(embedding.getModel(), embedding.getModelVersion())) {
                if (!embedding.isDownloadPretrained()) {
                    throw new IllegalStateException("Embedding GridFS 制品不存在且未允许远程初始化");
                }
                artifactService.downloadAndUpload(embedding.getModel(), embedding.getModelVersion(),
                        embedding.getPretrainedModel(), false);
            }
            artifactService.verifyIntegrity(embedding.getModel(), embedding.getModelVersion());
            model.ensureReady();
            log.info("Embedding 模型异步预热完成: model={}, version={}",
                    embedding.getModel(), embedding.getModelVersion());
        } catch (Exception exception) {
            log.warn("Embedding 模型异步预热失败: model={}, version={}, reason={}",
                    embedding.getModel(), embedding.getModelVersion(), exception.getMessage());
        }
    }

    private void warmupSentiment() {
        try {
            if (!artifactService.exists(sentimentConfig.getModelName(), sentimentConfig.getModelVersion())) {
                if (!sentimentConfig.isDownloadPretrained()) {
                    throw new IllegalStateException("Sentiment GridFS 制品不存在且未允许远程初始化");
                }
                artifactService.downloadAndUpload(sentimentConfig.getModelName(),
                        sentimentConfig.getModelVersion(), sentimentConfig.getPretrainedModel(), true);
            }
            artifactService.verifyIntegrity(sentimentConfig.getModelName(), sentimentConfig.getModelVersion());
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
// AI_GENERATE_END --
