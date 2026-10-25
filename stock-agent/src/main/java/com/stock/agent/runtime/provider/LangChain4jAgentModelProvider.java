// AI_GENERATE_START-------------------------------------
package com.stock.agent.runtime.provider;

import com.stock.agent.runtime.agent.StrategyAgent;
import com.stock.agent.runtime.api.AgentModelProvider;
import com.stock.agent.runtime.api.AgentModelProviderStatus;
import com.stock.agent.runtime.api.AgentProviderHealthStatus;
import com.stock.agent.runtime.api.AgentRuntimeRequest;
import com.stock.agent.runtime.api.AgentRuntimeResponse;
import com.stock.agent.runtime.api.AgentRuntimeStatus;
import com.stock.agent.tooling.capability.AgentToolCatalog;
import com.stock.agent.shared.config.AgentProperties;
import com.stock.agent.runtime.concurrency.AgentConversationLockService;
import com.stock.agent.runtime.memory.AgentChatMemoryStore;
import com.stock.agent.shared.error.FactSourceUnavailableException;
import com.stock.agent.tooling.tool.AgentToolContextHolder;
import com.stock.agent.tooling.tool.ToolExecutionContext;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Set;
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
@Component
public class LangChain4jAgentModelProvider implements AgentModelProvider {


    private final AgentProperties properties;
    private final AgentChatMemoryStore chatMemoryService;
    private final AgentCircuitBreaker circuitBreaker;
    private final AgentProviderModeManager providerModeManager;
    private final AgentApiBudgetManager apiBudgetService;
    private final AgentToolCatalog toolCatalog;
    private final AgentConversationLockService conversationLockService;
    private final AgentProviderClientRegistry clientRegistry;
    private final AgentProviderHealthChecker healthService;
    private final ExecutorService modelExecutor;

    /**
     * 创建模型调用线程池，使用配置限制并发，避免模型请求无限扩张。
     */
    public LangChain4jAgentModelProvider(AgentProperties properties,
                                         AgentChatMemoryStore chatMemoryService,
                                         AgentCircuitBreaker circuitBreaker,
                                         AgentProviderModeManager providerModeManager,
                                         AgentApiBudgetManager apiBudgetService,
                                         AgentToolCatalog toolCatalog,
                                         AgentConversationLockService conversationLockService,
                                         AgentProviderClientRegistry clientRegistry,
                                         AgentProviderHealthChecker healthService) {
        this.properties = properties;
        this.chatMemoryService = chatMemoryService;
        this.circuitBreaker = circuitBreaker;
        this.providerModeManager = providerModeManager;
        this.apiBudgetService = apiBudgetService;
        this.toolCatalog = toolCatalog;
        this.conversationLockService = conversationLockService;
        this.clientRegistry = clientRegistry;
        this.healthService = healthService;
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
            AgentApiBudgetManager.BudgetReservation budgetReservation = null;
            boolean budgetSettled = false;
            if (!circuitBreaker.allowRequest(provider)) {
                lastErrorCode = "AGENT_CIRCUIT_OPEN";
                clientRegistry.updateStatus(provider, lastErrorCode);
                log.warn("[Agent][Provider] taskId={} sessionId={} provider={} model={} status=SKIPPED reason=AGENT_CIRCUIT_OPEN",
                        request.getTaskId(), request.getSessionId(), provider, clientRegistry.modelName(provider));
                continue;
            }
            try {
                ChatModel model = clientRegistry.model(provider);
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
                        request.getTaskId(), request.getSessionId(), provider, clientRegistry.modelName(provider),
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
                            .performanceYearMonth(request.getPerformanceYearMonth())
                            .build();
                    AgentToolContextHolder.set(context);
                    try {
                        log.info("[Agent][Model] taskId={} sessionId={} provider={} model={} status=RUNNING allowedToolCount={}",
                                request.getTaskId(), request.getSessionId(), provider, clientRegistry.modelName(provider),
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
                    clientRegistry.updateStatus(provider, "TIMEOUT");
                    throw new IllegalStateException(provider + " 模型调用超时", exception);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(provider + " 模型调用被中断", exception);
                } catch (java.util.concurrent.ExecutionException exception) {
                    FactSourceUnavailableException factSourceException = findFactSourceException(exception);
                    if (factSourceException != null) {
                        throw factSourceException;
                    }
                    Throwable cause = rootCause(exception);
                    throw new IllegalStateException(provider + " 模型调用失败", cause);
                }
                String content = modelResult == null ? null : modelResult.content();
                if (!StringUtils.hasText(content)) {
                    throw new IllegalStateException("模型未返回文本内容");
                }
                if (budgetReservation != null) {
                    apiBudgetService.settleSuccess(budgetReservation, modelResult.tokenUsage());
                    budgetSettled = true;
                }
                clientRegistry.updateStatus(provider, "READY");
                circuitBreaker.recordSuccess(provider);
                chatMemoryService.stage(request.getTaskId(), request.getSessionId(), memory);
                log.info("[Agent][Model] taskId={} sessionId={} provider={} model={} status=COMPLETED contentChars={} elapsedMs={}",
                        request.getTaskId(), request.getSessionId(), provider, clientRegistry.modelName(provider), content.length(),
                        elapsedMillis(providerStartedAt));
                return AgentRuntimeResponse.builder()
                        .status(AgentRuntimeStatus.COMPLETED)
                        .provider(provider)
                        .model(clientRegistry.modelName(provider))
                        .content(limit(content))
                        .evidenceSummary(request.getEvidenceSummary())
                        .build();
            } catch (FactSourceUnavailableException exception) {
                if (budgetReservation != null && !budgetSettled) {
                    apiBudgetService.settleConservatively(budgetReservation,
                            FactSourceUnavailableException.ERROR_CODE);
                }
                log.warn("[Agent][Tool] taskId={} sessionId={} provider={} status=FAILED errorCode={}",
                        request.getTaskId(), request.getSessionId(), provider,
                        FactSourceUnavailableException.ERROR_CODE);
                return failure(request, FactSourceUnavailableException.ERROR_CODE, exception.getMessage());
            } catch (AgentApiBudgetManager.BudgetControlException exception) {
                String errorCode = exception.getErrorCode();
                clientRegistry.updateStatus(provider, errorCode);
                log.warn("[Agent][Budget] taskId={} sessionId={} provider={} model={} status=REJECTED errorCode={} message={}",
                        request.getTaskId(), request.getSessionId(), provider, clientRegistry.modelName(provider), errorCode,
                        safeErrorMessage(exception.getMessage()));
                return failure(request, errorCode, exception.getMessage());
            } catch (RuntimeException exception) {
                if (budgetReservation != null && !budgetSettled) {
                    apiBudgetService.settleConservatively(budgetReservation,
                            rootCause(exception).getClass().getSimpleName());
                }
                circuitBreaker.recordFailure(provider);
                String errorCode = clientRegistry.errorCode(provider, exception);
                lastErrorCode = errorCode;
                clientRegistry.updateStatus(provider, errorCode);
                log.warn("[Agent][Model] taskId={} sessionId={} provider={} model={} status=FAILED errorCode={} causeType={} causeMessage={} elapsedMs={}",
                        request.getTaskId(), request.getSessionId(), provider, clientRegistry.modelName(provider), errorCode,
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
                .deepseekConfigured(clientRegistry.isConfigured("deepseek"))
                .ollamaEnabled(properties.getOllama().isEnabled())
                .ollamaConfigured(clientRegistry.isConfigured("ollama"))
                .deepseekStatus(clientRegistry.statusFor("deepseek"))
                .ollamaStatus(clientRegistry.statusFor("ollama"))
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
        return healthService.checkAll();
    }

    /**
     * 仅对指定 Provider 发起不消耗 Token 的模型列表检查。
     *
     * @param provider Provider 名称
     * @return Provider 是否可用
     */
    @Override
    public boolean healthCheck(String provider) {
        return healthService.check(provider);
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
            case "deepseek" -> clientRegistry.isConfigured("deepseek")
                    ? ProviderSelection.of("deepseek")
                    : ProviderSelection.unavailable("DEEPSEEK_NOT_CONFIGURED", "DeepSeek 未启用或 API Key、地址、模型配置不完整");
            case "ollama" -> clientRegistry.isConfigured("ollama")
                    ? ProviderSelection.of("ollama")
                    : ProviderSelection.unavailable("OLLAMA_NOT_CONFIGURED", "Ollama 未启用或地址、模型配置不完整");
            case "auto" -> {
                if (clientRegistry.isConfigured("deepseek")) {
                    yield ProviderSelection.of("deepseek", clientRegistry.isConfigured("ollama") ? "ollama" : null);
                }
                if (clientRegistry.isConfigured("ollama")) {
                    yield ProviderSelection.of("ollama");
                }
                yield ProviderSelection.unavailable("NO_AGENT_PROVIDER_ENABLED", "DeepSeek 和 Ollama 均未启用或配置不完整");
            }
            case "disabled" -> ProviderSelection.unavailable("AGENT_DISABLED", "Agent Provider 已关闭");
            default -> ProviderSelection.unavailable("INVALID_AGENT_PROVIDER", "不支持的 Agent Provider: " + provider);
        };
    }


    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private FactSourceUnavailableException findFactSourceException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != current) {
            if (current instanceof FactSourceUnavailableException factSourceException) {
                return factSourceException;
            }
            current = current.getCause();
        }
        return null;
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
// AI_GENERATE_END------------------------------------
