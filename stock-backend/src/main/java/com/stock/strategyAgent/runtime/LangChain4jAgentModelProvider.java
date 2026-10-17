// AI_GENERATE_START -----
package com.stock.strategyAgent.runtime;

import com.stock.strategyAgent.config.AgentProperties;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 LangChain4j ChatModel 的双 Provider Agent 模型适配器。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Primary
@Service
@RequiredArgsConstructor
public class LangChain4jAgentModelProvider implements AgentModelProvider {

    private final AgentProperties properties;
    private final AgentChatMemoryService chatMemoryService;
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();

    /**
     * 使用 LangChain4j 执行结构化 Runtime 请求。
     *
     * @param request Runtime 请求
     * @return Runtime 响应
     */
    @Override
    public AgentRuntimeResponse generate(AgentRuntimeRequest request) {
        if (request == null) {
            return AgentRuntimeResponse.builder()
                    .status(AgentRuntimeStatus.FAILED)
                    .provider(providerName())
                    .errorCode("INVALID_RUNTIME_REQUEST")
                    .content("Runtime 请求不能为空")
                    .build();
        }
        if (!properties.isEnabled()) {
            return degraded(request, "AGENT_DISABLED", "Agent 未启用");
        }
        ProviderSelection selection = selectProvider();
        if (!selection.available()) {
            return failure(request, selection.errorCode(), selection.message());
        }
        for (String provider : selection.providers()) {
            try {
                ChatModel model = models.computeIfAbsent(provider, this::createModel);
                ChatMemory memory = chatMemoryService.get(request.getSessionId());
                memory.add(SystemMessage.from(request.getSystemPrompt()));
                memory.add(UserMessage.from(request.getUserPrompt()));
                String content = model.chat(memory.messages()).aiMessage().text();
                if (!StringUtils.hasText(content)) {
                    throw new IllegalStateException("模型未返回文本内容");
                }
                memory.add(dev.langchain4j.data.message.AiMessage.from(content));
                return AgentRuntimeResponse.builder()
                        .status(AgentRuntimeStatus.COMPLETED)
                        .provider(provider)
                        .model(modelName(provider))
                        .content(limit(content))
                        .evidenceSummary(request.getEvidenceSummary())
                        .build();
            } catch (RuntimeException exception) {
                log.warn("[Agent] Provider 调用失败: provider={}, errorCode=MODEL_CALL_FAILED", provider);
            }
        }
        return degraded(request, "ALL_AGENT_PROVIDERS_UNAVAILABLE",
                "DeepSeek 和 Ollama 均不可用，请检查 Provider 开关、地址、API Key 或本地模型");
    }

    /**
     * 返回提供方标识。
     *
     * @return 提供方名称
     */
    @Override
    public String providerName() {
        return "langchain4j-router:" + properties.getProvider();
    }

    /**
     * 返回两个 Provider 的脱敏配置状态。
     *
     * @return Provider 状态
     */
    public AgentModelProviderStatus status() {
        return AgentModelProviderStatus.builder()
                .provider(properties.getProvider())
                .deepseekEnabled(properties.getDeepseek().isEnabled())
                .deepseekConfigured(isDeepSeekConfigured())
                .ollamaEnabled(properties.getOllama().isEnabled())
                .ollamaConfigured(isOllamaConfigured())
                .build();
    }

    private AgentRuntimeResponse degraded(AgentRuntimeRequest request, String errorCode, String reason) {
        return AgentRuntimeResponse.builder()
                .status(AgentRuntimeStatus.DEGRADED)
                .provider(providerName())
                .content(reason + "，已保留结构化事实摘要。")
                .evidenceSummary(request.getEvidenceSummary())
                .errorCode(errorCode)
                .build();
    }

    private AgentRuntimeResponse failure(AgentRuntimeRequest request, String errorCode, String reason) {
        return AgentRuntimeResponse.builder()
                .status(AgentRuntimeStatus.FAILED)
                .provider(providerName())
                .content(reason)
                .evidenceSummary(request.getEvidenceSummary())
                .errorCode(errorCode)
                .build();
    }

    private ProviderSelection selectProvider() {
        String provider = properties.getProvider() == null ? "disabled" : properties.getProvider().trim().toLowerCase();
        return switch (provider) {
            case "deepseek" -> isDeepSeekConfigured()
                    ? ProviderSelection.of("deepseek")
                    : ProviderSelection.unavailable("DEEPSEEK_NOT_CONFIGURED", "DeepSeek 未启用或 API Key、地址、模型配置不完整");
            case "ollama" -> isOllamaConfigured()
                    ? ProviderSelection.of("ollama")
                    : ProviderSelection.unavailable("OLLAMA_NOT_CONFIGURED", "Ollama 未启用或地址、模型配置不完整");
            case "auto" -> {
                if (isDeepSeekConfigured()) {
                    yield ProviderSelection.of("deepseek", isOllamaConfigured() ? "ollama" : null);
                }
                if (isOllamaConfigured()) {
                    yield ProviderSelection.of("ollama");
                }
                yield ProviderSelection.unavailable("NO_AGENT_PROVIDER_ENABLED", "DeepSeek 和 Ollama 均未启用或配置不完整");
            }
            case "disabled" -> ProviderSelection.unavailable("AGENT_DISABLED", "Agent Provider 已关闭");
            default -> ProviderSelection.unavailable("INVALID_AGENT_PROVIDER", "不支持的 Agent Provider: " + provider);
        };
    }

    private ChatModel createModel(String provider) {
        if ("deepseek".equals(provider)) {
            AgentProperties.DeepSeekProperties config = properties.getDeepseek();
            return OpenAiChatModel.builder()
                    .baseUrl(config.getBaseUrl())
                    .apiKey(config.getApiKey())
                    .modelName(config.getModel())
                    .build();
        }
        AgentProperties.OllamaProperties config = properties.getOllama();
        return OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(StringUtils.hasText(config.getApiKey()) ? config.getApiKey() : "ollama")
                .modelName(config.getModel())
                .build();
    }

    private boolean isDeepSeekConfigured() {
        AgentProperties.DeepSeekProperties config = properties.getDeepseek();
        return config != null && config.isEnabled() && StringUtils.hasText(config.getBaseUrl())
                && StringUtils.hasText(config.getApiKey()) && StringUtils.hasText(config.getModel());
    }

    private boolean isOllamaConfigured() {
        AgentProperties.OllamaProperties config = properties.getOllama();
        return config != null && config.isEnabled() && StringUtils.hasText(config.getBaseUrl())
                && StringUtils.hasText(config.getModel());
    }

    private String modelName(String provider) {
        return "deepseek".equals(provider) ? properties.getDeepseek().getModel() : properties.getOllama().getModel();
    }

    private String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > properties.getMaxOutputChars()
                ? value.substring(0, properties.getMaxOutputChars()) : value;
    }

    private record ProviderSelection(boolean available, String errorCode, String message, java.util.List<String> providers) {

        private static ProviderSelection of(String first, String... fallback) {
            java.util.List<String> providers = new java.util.ArrayList<>();
            providers.add(first);
            if (fallback != null) {
                for (String item : fallback) {
                    if (StringUtils.hasText(item)) {
                        providers.add(item);
                    }
                }
            }
            return new ProviderSelection(true, null, null, providers);
        }

        private static ProviderSelection unavailable(String errorCode, String message) {
            return new ProviderSelection(false, errorCode, message, java.util.List.of());
        }
    }
}
// AI_GENERATE_END -----
