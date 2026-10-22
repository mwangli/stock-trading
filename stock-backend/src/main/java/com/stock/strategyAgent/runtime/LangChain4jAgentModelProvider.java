// AI_GENERATE_START -----------------------------
package com.stock.strategyAgent.runtime;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.stock.strategyAgent.capability.AgentToolCatalog;
import com.stock.strategyAgent.config.AgentProperties;
import com.stock.strategyAgent.service.AgentConversationLockService;
import com.stock.strategyAgent.tool.AgentToolContextHolder;
import com.stock.strategyAgent.tool.ToolExecutionContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private static final Duration OLLAMA_HEALTH_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEEPSEEK_HEALTH_TIMEOUT = Duration.ofSeconds(5);

    private final AgentProperties properties;
    private final AgentChatMemoryService chatMemoryService;
    private final AgentCircuitBreaker circuitBreaker;
    private final AgentProviderModeManager providerModeManager;
    private final AgentApiBudgetService apiBudgetService;
    private final AgentToolCatalog toolCatalog;
    private final AgentConversationLockService conversationLockService;
    private final Map<String, ChatModel> models = new ConcurrentHashMap<>();
    private final Map<String, String> providerStatuses = new ConcurrentHashMap<>();
    private final ExecutorService modelExecutor;
    private final HttpClient healthHttpClient;

    /**
     * 创建模型调用线程池，使用配置限制并发，避免模型请求无限扩张。
     */
    public LangChain4jAgentModelProvider(AgentProperties properties,
                                         AgentChatMemoryService chatMemoryService,
                                         AgentCircuitBreaker circuitBreaker,
                                         AgentProviderModeManager providerModeManager,
                                         AgentApiBudgetService apiBudgetService,
                                         AgentToolCatalog toolCatalog,
                                         AgentConversationLockService conversationLockService) {
        this.properties = properties;
        this.chatMemoryService = chatMemoryService;
        this.circuitBreaker = circuitBreaker;
        this.providerModeManager = providerModeManager;
        this.apiBudgetService = apiBudgetService;
        this.toolCatalog = toolCatalog;
        this.conversationLockService = conversationLockService;
        this.modelExecutor = Executors.newFixedThreadPool(Math.max(properties.getMaxConcurrency(), 1));
        this.healthHttpClient = HttpClient.newBuilder()
                .connectTimeout(DEEPSEEK_HEALTH_TIMEOUT)
                .build();
    }

    /**
     * 应用启动时预创建已配置的远程模型客户端。
     *
     * 客户端创建只校验本地配置，不在启动阶段发起网络请求；真实服务可用性由健康检查主动确认。
     */
    @PostConstruct
    public void preloadConfiguredModels() {
        if (!properties.isEnabled()) {
            return;
        }
        preloadProvider("deepseek", isDeepSeekConfigured());
        preloadProvider("ollama", isOllamaConfigured());
    }

    private void preloadProvider(String provider, boolean configured) {
        if (!configured) {
            providerStatuses.put(provider, "NOT_CONFIGURED");
            return;
        }
        try {
            models.put(provider, createModel(provider));
            providerStatuses.put(provider, "PRELOADED");
            log.info("Agent Provider 客户端预加载完成: provider={}, model={}", provider, modelName(provider));
        } catch (RuntimeException exception) {
            providerStatuses.put(provider, providerErrorCode(provider, exception));
            log.warn("Agent Provider 客户端预加载失败: provider={}, causeType={}",
                    provider, exception.getClass().getSimpleName());
        }
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
        if (!StringUtils.hasText(request.getSessionId())) {
            return failure(request, "SESSION_REQUIRED", "Runtime 会话标识不能为空");
        }
        if (!StringUtils.hasText(request.getTaskId())) {
            return failure(request, "TASK_REQUIRED", "Runtime 任务标识不能为空");
        }
        return conversationLockService.execute(request.getSessionId(), () -> generateLocked(request));
    }

    private AgentRuntimeResponse generateLocked(AgentRuntimeRequest request) {
        if (!properties.isEnabled()) {
            return degraded(request, "AGENT_DISABLED", "Agent 未启用");
        }
        ProviderSelection selection = selectProvider();
        if (!selection.available()) {
            return failure(request, selection.errorCode(), selection.message());
        }
        log.info("[Agent][Provider] taskId={} sessionId={} outputType={} status=SELECTED providers={} timeoutMs={} maxConcurrency={}",
                request.getTaskId(), request.getSessionId(), request.getOutputType(), selection.providers(),
                properties.getTimeoutMs(), properties.getMaxConcurrency());
        String lastErrorCode = "MODEL_CALL_FAILED";
        for (String provider : selection.providers()) {
            long providerStartedAt = System.nanoTime();
            AgentApiBudgetService.BudgetReservation budgetReservation = null;
            boolean budgetSettled = false;
            if (!circuitBreaker.allowRequest(provider)) {
                lastErrorCode = "AGENT_CIRCUIT_OPEN";
                providerStatuses.put(provider, lastErrorCode);
                log.warn("[Agent][Provider] taskId={} sessionId={} provider={} model={} status=SKIPPED reason=AGENT_CIRCUIT_OPEN",
                        request.getTaskId(), request.getSessionId(), provider, modelName(provider));
                continue;
            }
            try {
                ChatModel model = models.computeIfAbsent(provider, this::createModel);
                ChatMemory memory = chatMemoryService.snapshot(request.getSessionId());
                if ("deepseek".equals(provider)) {
                    long estimatedInputTokens = apiBudgetService.estimateInputTokens(
                            request.getSystemPrompt(), request.getUserPrompt(), memory.messages());
                    budgetReservation = apiBudgetService.reserveDeepSeek(
                            estimatedInputTokens, properties.getDeepseek().getMaxTokens());
                }
                AiServices<StrategyAgent> assistantBuilder = AiServices.builder(StrategyAgent.class)
                        .chatModel(model)
                        .chatMemory(memory);
                List<Object> selectedTools = toolCatalog.resolve(request.getAllowedTools());
                if (!selectedTools.isEmpty()) {
                    assistantBuilder.tools(selectedTools);
                }
                StrategyAgent assistant = assistantBuilder.build();
                int chatMemoryMessages = memory.messages().size();
                log.info("[Agent][Model] taskId={} sessionId={} provider={} model={} status=QUEUED promptChars={} evidenceRefCount={} chatMemoryMessages={}",
                        request.getTaskId(), request.getSessionId(), provider, modelName(provider),
                        request.getUserPrompt().length(), request.getEvidenceRefs() == null ? 0 : request.getEvidenceRefs().size(),
                        chatMemoryMessages);
                Future<Result<String>> call = modelExecutor.submit(() -> {
                    ToolExecutionContext context = ToolExecutionContext.builder()
                            .traceId(request.getTaskId() + ":" + provider)
                            .sessionId(request.getSessionId())
                            .taskId(request.getTaskId())
                            .userScope(request.getUserScope())
                            .allowedToolNames(request.getAllowedTools() == null ? Set.of()
                                    : request.getAllowedTools().stream().map(tool -> tool.toolName()).collect(
                                            java.util.stream.Collectors.toUnmodifiableSet()))
                            .build();
                    AgentToolContextHolder.set(context);
                    try {
                        log.info("[Agent][Model] taskId={} sessionId={} provider={} model={} status=RUNNING allowedToolCount={}",
                                request.getTaskId(), request.getSessionId(), provider, modelName(provider),
                                selectedTools.size());
                        return assistant.chat(request.getUserPrompt(), request.getSystemPrompt());
                    } finally {
                        AgentToolContextHolder.clear();
                    }
                });
                Result<String> modelResult;
                try {
                    modelResult = call.get(Math.max(properties.getTimeoutMs(), 1L), TimeUnit.MILLISECONDS);
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
                String content = modelResult == null ? null : modelResult.content();
                if (!StringUtils.hasText(content)) {
                    throw new IllegalStateException("模型未返回文本内容");
                }
                if (budgetReservation != null) {
                    apiBudgetService.settleSuccess(budgetReservation, modelResult.tokenUsage());
                    budgetSettled = true;
                }
                providerStatuses.put(provider, "READY");
                circuitBreaker.recordSuccess(provider);
                chatMemoryService.stage(request.getTaskId(), request.getSessionId(), memory);
                log.info("[Agent][Model] taskId={} sessionId={} provider={} model={} status=COMPLETED contentChars={} elapsedMs={}",
                        request.getTaskId(), request.getSessionId(), provider, modelName(provider), content.length(),
                        elapsedMillis(providerStartedAt));
                return AgentRuntimeResponse.builder()
                        .status(AgentRuntimeStatus.COMPLETED)
                        .provider(provider)
                        .model(modelName(provider))
                        .content(limit(content))
                        .evidenceSummary(request.getEvidenceSummary())
                        .build();
            } catch (AgentApiBudgetService.BudgetControlException exception) {
                String errorCode = exception.getErrorCode();
                providerStatuses.put(provider, errorCode);
                log.warn("[Agent][Budget] taskId={} sessionId={} provider={} model={} status=REJECTED errorCode={} message={}",
                        request.getTaskId(), request.getSessionId(), provider, modelName(provider), errorCode,
                        safeErrorMessage(exception.getMessage()));
                return failure(request, errorCode, exception.getMessage());
            } catch (RuntimeException exception) {
                if (budgetReservation != null && !budgetSettled) {
                    apiBudgetService.settleConservatively(budgetReservation,
                            rootCause(exception).getClass().getSimpleName());
                }
                circuitBreaker.recordFailure(provider);
                String errorCode = providerErrorCode(provider, exception);
                lastErrorCode = errorCode;
                providerStatuses.put(provider, errorCode);
                log.warn("[Agent][Model] taskId={} sessionId={} provider={} model={} status=FAILED errorCode={} causeType={} causeMessage={} elapsedMs={}",
                        request.getTaskId(), request.getSessionId(), provider, modelName(provider), errorCode,
                        rootCause(exception).getClass().getSimpleName(), safeErrorMessage(rootCause(exception).getMessage()),
                        elapsedMillis(providerStartedAt));
            }
        }
        String errorCode = selection.providers().size() == 1
                ? lastErrorCode : "ALL_AGENT_PROVIDERS_UNAVAILABLE";
        String message = "AGENT_CIRCUIT_OPEN".equals(errorCode)
                ? "模型 Provider 处于熔断状态，请稍后刷新健康状态后重试"
                : selection.providers().size() == 1
                        ? "模型 Provider 当前不可用，请检查地址、凭据或本地模型"
                        : "DeepSeek 和 Ollama 均不可用，请检查 Provider 开关、地址、API Key 或本地模型";
        return failure(request, errorCode,
                message);
    }

    /**
     * 返回提供方标识。
     *
     * @return 提供方名称
     */
    @Override
    public String providerName() {
        return providerModeManager.currentMode();
    }

    /**
     * 返回两个 Provider 的脱敏配置状态。
     *
     * @return Provider 状态
     */
    public AgentModelProviderStatus status() {
        return AgentModelProviderStatus.builder()
                .provider(providerModeManager.currentMode())
                .deepseekEnabled(properties.getDeepseek().isEnabled())
                .deepseekConfigured(isDeepSeekConfigured())
                .ollamaEnabled(properties.getOllama().isEnabled())
                .ollamaConfigured(isOllamaConfigured())
                .deepseekStatus(statusFor("deepseek", isDeepSeekConfigured()))
                .ollamaStatus(statusFor("ollama", isOllamaConfigured()))
                .deepseekCircuitState(circuitBreaker.snapshot("deepseek").getState())
                .ollamaCircuitState(circuitBreaker.snapshot("ollama").getState())
                .build();
    }

    /**
     * 分别对已配置的 DeepSeek 和 Ollama 执行不消耗Token的模型列表检查。
     *
     * @return 两个 Provider 的实际可用状态
     */
    @Override
    public AgentProviderHealthStatus healthCheck() {
        if (!properties.isEnabled()) {
            providerStatuses.put("deepseek", "AGENT_DISABLED");
            providerStatuses.put("ollama", "AGENT_DISABLED");
            return AgentProviderHealthStatus.builder().build();
        }
        return AgentProviderHealthStatus.builder()
                .deepseekAvailable(checkProvider("deepseek"))
                .ollamaAvailable(checkProvider("ollama"))
                .build();
    }

    /**
     * 仅对指定 Provider 发起不消耗Token的模型列表检查。
     *
     * @param provider Provider 名称
     * @return Provider 是否可用
     */
    @Override
    public boolean healthCheck(String provider) {
        if (!"deepseek".equals(provider) && !"ollama".equals(provider)) {
            return false;
        }
        return checkProvider(provider);
    }

    private boolean checkProvider(String provider) {
        if (("deepseek".equals(provider) && !isDeepSeekConfigured())
                || ("ollama".equals(provider) && !isOllamaConfigured())) {
            providerStatuses.put(provider, "NOT_CONFIGURED");
            return false;
        }
        if (!circuitBreaker.allowRequest(provider)) {
            providerStatuses.put(provider, "AGENT_CIRCUIT_OPEN");
            return false;
        }
        if ("ollama".equals(provider)) {
            return checkOllamaProvider();
        }
        return checkDeepSeekProvider();
    }

    private boolean checkDeepSeekProvider() {
        long startedAt = System.nanoTime();
        AgentProperties.DeepSeekProperties config = properties.getDeepseek();
        try {
            HttpRequest request = HttpRequest.newBuilder(deepSeekModelsUri())
                    .timeout(DEEPSEEK_HEALTH_TIMEOUT)
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = healthHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("DeepSeek 模型列表接口返回 HTTP " + response.statusCode());
            }
            JSONObject payload = JSON.parseObject(response.body());
            JSONArray modelsPayload = payload == null ? null : payload.getJSONArray("data");
            boolean modelAvailable = modelsPayload != null && modelsPayload.stream()
                    .filter(JSONObject.class::isInstance)
                    .map(JSONObject.class::cast)
                    .anyMatch(item -> config.getModel().equals(item.getString("id")));
            if (!modelAvailable) {
                providerStatuses.put("deepseek", "MODEL_NOT_FOUND");
                circuitBreaker.recordFailure("deepseek");
                log.warn("[Agent][Health] provider=deepseek model={} status=MODEL_NOT_FOUND elapsedMs={}",
                        config.getModel(), elapsedMillis(startedAt));
                return false;
            }
            providerStatuses.put("deepseek", "READY");
            circuitBreaker.recordSuccess("deepseek");
            log.info("[Agent][Health] provider=deepseek model={} status=READY check=MODEL_LIST elapsedMs={}",
                    config.getModel(), elapsedMillis(startedAt));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return recordDeepSeekHealthFailure(exception, startedAt);
        } catch (RuntimeException exception) {
            return recordDeepSeekHealthFailure(exception, startedAt);
        } catch (Exception exception) {
            return recordDeepSeekHealthFailure(exception, startedAt);
        }
    }

    private boolean recordDeepSeekHealthFailure(Exception exception, long startedAt) {
        circuitBreaker.recordFailure("deepseek");
        providerStatuses.put("deepseek", "DEEPSEEK_UNAVAILABLE");
        log.warn("[Agent][Health] provider=deepseek model={} status=DEEPSEEK_UNAVAILABLE causeType={} causeMessage={} elapsedMs={}",
                properties.getDeepseek().getModel(), exception.getClass().getSimpleName(),
                safeErrorMessage(exception.getMessage()), elapsedMillis(startedAt));
        return false;
    }

    private boolean checkOllamaProvider() {
        long startedAt = System.nanoTime();
        String model = properties.getOllama().getModel();
        try {
            HttpRequest request = HttpRequest.newBuilder(ollamaTagsUri())
                    .timeout(OLLAMA_HEALTH_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = healthHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama 模型列表接口返回 HTTP " + response.statusCode());
            }
            JSONObject payload = JSON.parseObject(response.body());
            JSONArray modelsPayload = payload == null ? null : payload.getJSONArray("models");
            boolean modelAvailable = modelsPayload != null && modelsPayload.stream()
                    .filter(JSONObject.class::isInstance)
                    .map(JSONObject.class::cast)
                    .anyMatch(item -> model.equals(item.getString("name")) || model.equals(item.getString("model")));
            if (!modelAvailable) {
                providerStatuses.put("ollama", "MODEL_NOT_FOUND");
                circuitBreaker.recordFailure("ollama");
                log.warn("[Agent][Health] provider=ollama model={} status=MODEL_NOT_FOUND elapsedMs={}",
                        model, elapsedMillis(startedAt));
                return false;
            }
            providerStatuses.put("ollama", "READY");
            circuitBreaker.recordSuccess("ollama");
            log.info("[Agent][Health] provider=ollama model={} status=READY check=MODEL_LIST elapsedMs={}",
                    model, elapsedMillis(startedAt));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return recordOllamaHealthFailure(exception, startedAt);
        } catch (Exception exception) {
            return recordOllamaHealthFailure(exception, startedAt);
        }
    }

    private boolean recordOllamaHealthFailure(Exception exception, long startedAt) {
        circuitBreaker.recordFailure("ollama");
        providerStatuses.put("ollama", "OLLAMA_UNAVAILABLE");
        log.warn("[Agent][Health] provider=ollama model={} status=OLLAMA_UNAVAILABLE causeType={} causeMessage={} elapsedMs={}",
                properties.getOllama().getModel(), exception.getClass().getSimpleName(),
                safeErrorMessage(exception.getMessage()), elapsedMillis(startedAt));
        return false;
    }

    private URI ollamaTagsUri() {
        String baseUrl = properties.getOllama().getBaseUrl().trim().replaceAll("/+$", "");
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        return URI.create(baseUrl + "/api/tags");
    }

    private URI deepSeekModelsUri() {
        String baseUrl = properties.getDeepseek().getBaseUrl().trim().replaceAll("/+$", "");
        return URI.create(baseUrl + "/models");
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
        String provider = providerModeManager.currentMode();
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

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
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
// AI_GENERATE_END -----------------------------
