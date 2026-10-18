// AI_GENERATE_START ---------
package com.stock.strategyAgent.runtime;

import com.stock.strategyAgent.config.AgentProperties;
import com.stock.strategyAgent.tool.AgentToolContextHolder;
import com.stock.strategyAgent.tool.ToolExecutionContext;
import com.stock.strategyAgent.tool.read.BrokerFactQueryTool;
import com.stock.strategyAgent.tool.read.JobDiagnosticTool;
import com.stock.strategyAgent.tool.read.PredictionCandidateQueryTool;
import com.stock.strategyAgent.tool.read.StrategyStateQueryTool;
import com.stock.strategyAgent.tool.read.TradingCalendarQueryTool;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 基于 LangChain4j AiServices 的双 Provider Agent 模型适配器。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Primary
@Service
public class LangChain4jAgentModelProvider implements AgentModelProvider {

    private final AgentProperties properties;
    private final AgentChatMemoryService chatMemoryService;
    private final TradingCalendarQueryTool tradingCalendarQueryTool;
    private final StrategyStateQueryTool strategyStateQueryTool;
    private final PredictionCandidateQueryTool predictionCandidateQueryTool;
    private final JobDiagnosticTool jobDiagnosticTool;
    private final BrokerFactQueryTool brokerFactQueryTool;
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, String> providerStatuses = new ConcurrentHashMap<>();
    private final ExecutorService modelExecutor;

    /**
     * 创建模型调用线程池，使用配置限制并发，避免模型请求无限扩张。
     */
    public LangChain4jAgentModelProvider(AgentProperties properties,
                                         AgentChatMemoryService chatMemoryService,
                                         TradingCalendarQueryTool tradingCalendarQueryTool,
                                         StrategyStateQueryTool strategyStateQueryTool,
                                         PredictionCandidateQueryTool predictionCandidateQueryTool,
                                         JobDiagnosticTool jobDiagnosticTool,
                                         BrokerFactQueryTool brokerFactQueryTool) {
        this.properties = properties;
        this.chatMemoryService = chatMemoryService;
        this.tradingCalendarQueryTool = tradingCalendarQueryTool;
        this.strategyStateQueryTool = strategyStateQueryTool;
        this.predictionCandidateQueryTool = predictionCandidateQueryTool;
        this.jobDiagnosticTool = jobDiagnosticTool;
        this.brokerFactQueryTool = brokerFactQueryTool;
        this.modelExecutor = Executors.newFixedThreadPool(Math.max(properties.getMaxConcurrency(), 1));
    }

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
        String lastErrorCode = "MODEL_CALL_FAILED";
        for (String provider : selection.providers()) {
            try {
                ChatModel model = models.computeIfAbsent(provider, this::createModel);
                ChatMemory memory = chatMemoryService.get(request.getSessionId());
                StrategyAgent assistant = AiServices.builder(StrategyAgent.class)
                        .chatModel(model)
                        .chatMemory(memory)
                        .tools(tradingCalendarQueryTool, strategyStateQueryTool, predictionCandidateQueryTool,
                                jobDiagnosticTool, brokerFactQueryTool)
                        .build();
                ToolExecutionContext context = ToolExecutionContext.builder()
                        .traceId(request.getTaskId())
                        .sessionId(request.getSessionId())
                        .taskId(request.getTaskId())
                        .userScope(request.getUserScope())
                        .build();
                Future<String> call = modelExecutor.submit(() -> {
                    AgentToolContextHolder.set(context);
                    try {
                        return assistant.chat(request.getUserPrompt(), request.getSystemPrompt());
                    } finally {
                        AgentToolContextHolder.clear();
                    }
                });
                String content;
                try {
                    content = call.get(Math.max(properties.getTimeoutMs(), 1L), TimeUnit.MILLISECONDS);
                } catch (TimeoutException exception) {
                    call.cancel(true);
                    providerStatuses.put(provider, "TIMEOUT");
                    throw new IllegalStateException(provider + " 模型调用超时", exception);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(provider + " 模型调用被中断", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    throw new IllegalStateException(provider + " 模型调用失败", exception.getCause());
                }
                if (!StringUtils.hasText(content)) {
                    throw new IllegalStateException("模型未返回文本内容");
                }
                providerStatuses.put(provider, "READY");
                return AgentRuntimeResponse.builder()
                        .status(AgentRuntimeStatus.COMPLETED)
                        .provider(provider)
                        .model(modelName(provider))
                        .content(limit(content))
                        .evidenceSummary(request.getEvidenceSummary())
                        .build();
            } catch (RuntimeException exception) {
                String errorCode = providerErrorCode(provider, exception);
                lastErrorCode = errorCode;
                providerStatuses.put(provider, errorCode);
                log.warn("[Agent] Provider 调用失败: provider={}, errorCode={}, causeType={}, causeMessage={}",
                        provider, errorCode, rootCause(exception).getClass().getSimpleName(),
                        safeErrorMessage(rootCause(exception).getMessage()));
            }
        }
        String errorCode = selection.providers().size() == 1
                ? lastErrorCode : "ALL_AGENT_PROVIDERS_UNAVAILABLE";
        return failure(request, errorCode,
                selection.providers().size() == 1
                        ? "模型 Provider 当前不可用，请检查地址、凭据或本地模型"
                        : "DeepSeek 和 Ollama 均不可用，请检查 Provider 开关、地址、API Key 或本地模型");
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
                .deepseekStatus(statusFor("deepseek", isDeepSeekConfigured()))
                .ollamaStatus(statusFor("ollama", isOllamaConfigured()))
                .build();
    }

    /**
     * 关闭模型调用线程池。
     */
    @PreDestroy
    public void shutdown() {
        modelExecutor.shutdownNow();
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
                .think(config.isThink())
                .numPredict(Math.max(config.getMaxTokens(), 1))
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

    private String statusFor(String provider, boolean configured) {
        if (!configured) {
            return "DISABLED";
        }
        return providerStatuses.getOrDefault(provider, "CONFIGURED");
    }

    private String providerErrorCode(String provider, RuntimeException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("timeout") || message.contains("超时")) {
            return "TIMEOUT";
        }
        if ("ollama".equals(provider) && (message.contains("404") || message.contains("not found"))) {
            return "MODEL_NOT_FOUND";
        }
        return "ollama".equals(provider) ? "OLLAMA_UNAVAILABLE" : "DEEPSEEK_UNAVAILABLE";
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeErrorMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        String sanitized = message.replaceAll("(?i)(authorization\\s*:\\s*bearer\\s+|api[-_ ]?key\\s*[=:]\\s*)[^\\s,;]+", "$1***")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "sk-***");
        return sanitized.substring(0, Math.min(sanitized.length(), 500));
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
// AI_GENERATE_END ---------
