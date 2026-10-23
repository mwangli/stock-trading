// AI_GENERATE_START---
package com.stock.agent.runtime;

import com.stock.agent.config.AgentProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Provider 客户端注册表。
 *
 * 负责 Provider 配置校验、模型客户端创建与缓存，以及脱敏运行状态维护。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AgentProviderClientRegistry {

    private final AgentProperties properties;
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, String> statuses = new ConcurrentHashMap<>();

    @PostConstruct
    void preloadConfiguredModels() {
        if (!properties.isEnabled()) {
            return;
        }
        preload("deepseek");
        preload("ollama");
    }

    ChatModel model(String provider) {
        return models.computeIfAbsent(provider, this::createModel);
    }

    boolean isConfigured(String provider) {
        if ("deepseek".equals(provider)) {
            AgentProperties.DeepSeekProperties config = properties.getDeepseek();
            return config != null && config.isEnabled() && StringUtils.hasText(config.getBaseUrl())
                    && StringUtils.hasText(config.getApiKey()) && StringUtils.hasText(config.getModel());
        }
        if ("ollama".equals(provider)) {
            AgentProperties.OllamaProperties config = properties.getOllama();
            return config != null && config.isEnabled() && StringUtils.hasText(config.getBaseUrl())
                    && StringUtils.hasText(config.getModel());
        }
        return false;
    }

    String modelName(String provider) {
        return "deepseek".equals(provider)
                ? properties.getDeepseek().getModel() : properties.getOllama().getModel();
    }

    String statusFor(String provider) {
        return isConfigured(provider) ? statuses.getOrDefault(provider, "CONFIGURED") : "DISABLED";
    }

    void updateStatus(String provider, String status) {
        statuses.put(provider, status);
    }

    String errorCode(String provider, RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("timeout") || message.contains("超时")) {
            return "TIMEOUT";
        }
        if ("ollama".equals(provider) && (message.contains("404") || message.contains("not found"))) {
            return "MODEL_NOT_FOUND";
        }
        return "ollama".equals(provider) ? "OLLAMA_UNAVAILABLE" : "DEEPSEEK_UNAVAILABLE";
    }

    private void preload(String provider) {
        if (!isConfigured(provider)) {
            statuses.put(provider, "NOT_CONFIGURED");
            return;
        }
        try {
            models.put(provider, createModel(provider));
            statuses.put(provider, "PRELOADED");
            log.info("Agent Provider 客户端预加载完成: provider={}, model={}", provider, modelName(provider));
        } catch (RuntimeException exception) {
            statuses.put(provider, errorCode(provider, exception));
            log.warn("Agent Provider 客户端预加载失败: provider={}, causeType={}",
                    provider, exception.getClass().getSimpleName());
        }
    }

    private ChatModel createModel(String provider) {
        if ("deepseek".equals(provider)) {
            AgentProperties.DeepSeekProperties config = properties.getDeepseek();
            return OpenAiChatModel.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .modelName(config.getModel())
                    .maxTokens(Math.max(config.getMaxTokens(), 1))
                    .customParameters(Map.of("thinking", Map.of(
                            "type", config.isThinkingEnabled() ? "enabled" : "disabled")))
                    .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1L)))
                    .maxRetries(0)
                    .build();
        }
        AgentProperties.OllamaProperties config = properties.getOllama();
        return OllamaChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .modelName(config.getModel())
                .timeout(Duration.ofMillis(Math.max(properties.getTimeoutMs(), 1L)))
                .maxRetries(0)
                .responseFormat(ResponseFormat.JSON)
                .think(config.isThink())
                .returnThinking(false)
                .numPredict(Math.max(config.getMaxTokens(), 1))
                .build();
    }
}
// AI_GENERATE_END---
