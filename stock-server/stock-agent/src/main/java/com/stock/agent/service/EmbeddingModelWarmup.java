// AI_GENERATE_START ------
package com.stock.agent.service;

import com.stock.agent.client.DjlEmbeddingModel;
import com.stock.agent.config.AgentProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 应用就绪后的本地 Embedding 模型预热服务。
 *
 * 在 HTTP 服务完成启动后主动加载 DJL 模型，使运行状态反映真实推理能力；
 * 预热失败只降级 RAG 和向量 Memory，不阻止 Agent 其他能力启动。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingModelWarmup {

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AgentProperties properties;

    /**
     * 应用就绪后加载本地 DJL Embedding 模型。
     */
    @Order(100)
    @EventListener(ApplicationReadyEvent.class)
    public void warmup() {
        if (!properties.isEnabled() || !properties.getEmbedding().isEnabled()) {
            return;
        }
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (!(embeddingModel instanceof DjlEmbeddingModel djlEmbeddingModel)) {
            return;
        }
        try {
            djlEmbeddingModel.ensureReady();
            log.info("Embedding 模型预热完成: model={}, version={}",
                    properties.getEmbedding().getModel(), properties.getEmbedding().getModelVersion());
        } catch (RuntimeException exception) {
            log.warn("Embedding 模型预热失败，RAG 与向量 Memory 保持降级: model={}, version={}, reason={}",
                    properties.getEmbedding().getModel(), properties.getEmbedding().getModelVersion(),
                    djlEmbeddingModel.lastLoadError());
        }
    }
}
// AI_GENERATE_END ------
