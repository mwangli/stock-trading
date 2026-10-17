// AI_GENERATE_START ---
package com.stock.strategyAgent.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 模型配置。
 * 仅在 Agent 显式启用且配置了 Embedding 模型时创建 Embedding Bean。
 * ChatModel 由运行时 Provider Router 按 DeepSeek/Ollama 配置懒加载，避免两个模型 Bean 产生歧义。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Configuration
public class LangChain4jConfiguration {

    /**
     * 创建 OpenAI-compatible EmbeddingModel。
     *
     * @param properties Agent 配置
     * @return LangChain4j EmbeddingModel
     */
    @Bean
    @ConditionalOnExpression("'${agent.enabled:false}' == 'true' && '${agent.embedding-model:}' != ''")
    public EmbeddingModel langChain4jEmbeddingModel(AgentProperties properties) {
        String baseUrl = properties.getDeepseek().getBaseUrl();
        String apiKey = properties.getDeepseek().getApiKey();
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(properties.getEmbeddingModel())
                .build();
    }
}
// AI_GENERATE_END ---
