// AI_GENERATE_START --
package com.stock.strategyAgent.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 模型配置。
 * 仅在 Agent 显式启用且配置了 OpenAI-compatible Provider 时创建外部模型 Bean。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Configuration
public class LangChain4jConfiguration {

    /**
     * 创建 OpenAI-compatible ChatModel。
     *
     * @param properties Agent 配置
     * @return LangChain4j ChatModel
     */
    @Bean
    @ConditionalOnExpression("'${agent.enabled:false}' == 'true' && '${agent.provider:}' == 'deepseek'")
    public ChatModel langChain4jChatModel(AgentProperties properties) {
        return OpenAiChatModel.builder()
                .baseUrl(properties.getApiUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .build();
    }

    /**
     * 创建 OpenAI-compatible EmbeddingModel。
     *
     * @param properties Agent 配置
     * @return LangChain4j EmbeddingModel
     */
    @Bean
    @ConditionalOnExpression("'${agent.enabled:false}' == 'true' && '${agent.embedding-model:}' != ''")
    public EmbeddingModel langChain4jEmbeddingModel(AgentProperties properties) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(properties.getApiUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getEmbeddingModel())
                .build();
    }
}
// AI_GENERATE_END --
