// AI_GENERATE_START-----------
package com.stock.agent.config;

import com.stock.agent.rag.DjlEmbeddingModel;
import com.stock.agent.rag.EmbeddingArtifactService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 模型配置。
 * 仅在 Agent 显式启用且配置了 Embedding 模型时创建 Embedding Bean。
 * ChatModel 由运行时 Provider Router 按 DeepSeek/Ollama 配置预创建客户端，避免两个模型 Bean 产生歧义。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Configuration
public class LangChain4jConfiguration {

    /**
     * 创建 EmbeddingModel。
     *
     * @param properties Agent 配置
     * @return LangChain4j EmbeddingModel
     */
    @Bean(name = "agentEmbeddingModel")
    @ConditionalOnExpression("'${agent.enabled:false}' == 'true' && '${agent.embedding.enabled:false}' == 'true' && '${agent.embedding.provider:disabled}' != 'disabled'")
    public EmbeddingModel agentEmbeddingModel(AgentProperties properties, EmbeddingArtifactService artifactService) {
        AgentProperties.EmbeddingProperties config = properties.getEmbedding();
        return switch (config.getProvider().trim().toLowerCase()) {
            case "djl" -> new DjlEmbeddingModel(config, artifactService);
            case "ollama" -> OllamaEmbeddingModel.builder()
                    .baseUrl(config.getBaseUrl())
                    .modelName(config.getModel())
                    .build();
            case "openaicompatible" -> OpenAiEmbeddingModel.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .modelName(config.getModel())
                    .build();
            default -> throw new IllegalStateException("不支持的 Embedding Provider: " + config.getProvider());
        };
    }
}
// AI_GENERATE_END-----------
