// AI_GENERATE_START-----
package com.stock.agent.runtime.service;

import com.stock.agent.interaction.model.AgentTaskStatus;

import com.stock.agent.shared.config.AgentProperties;
import com.stock.agent.runtime.dto.AgentHealthResponse;
import com.stock.agent.runtime.dto.ProviderModeRequest;
import com.stock.agent.runtime.dto.ProviderModeResponse;
import com.stock.agent.runtime.dto.RuntimeStatus;
import com.stock.agent.runtime.api.AgentModelProviderStatus;
import com.stock.agent.runtime.api.FactSourceStatus;
import com.stock.agent.runtime.api.FactSourceStatusPort;
import com.stock.agent.knowledge.rag.embedding.EmbeddingRuntimeStatus;
import com.stock.agent.knowledge.rag.embedding.LangChainEmbeddingService;
import com.stock.agent.interaction.repository.AgentTaskRepository;
import com.stock.agent.runtime.service.AgentRuntimeCoordinator;
import com.stock.agent.integration.client.DeepSeekWebSearchClient;
import com.stock.agent.knowledge.document.service.ProjectDocumentSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Agent 运行状态和 Provider 管理服务。
 *
 * 负责运行时状态聚合、健康检查和 Provider 切换，不参与会话或任务编排。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class AgentRuntimeManagementService {

    private final AgentTaskRepository taskRepository;
    private final AgentRuntimeCoordinator runtimeService;
    private final AgentProperties properties;
    private final LangChainEmbeddingService embeddingService;
    private final FactSourceStatusPort factSourceStatusPort;
    private final ProjectDocumentSearchService documentSearchService;
    private final DeepSeekWebSearchClient webSearchClient;

    /**
     * 获取 Agent 运行状态。
     *
     * @return 运行状态
     */
    public RuntimeStatus runtimeStatus() {
        var providerStatus = runtimeService.providerStatus();
        EmbeddingRuntimeStatus embeddingStatus = embeddingService.status();
        FactSourceStatus backendFactsStatus = factSourceStatusPort.status();
        return RuntimeStatus.builder()
                .enabled(properties.isEnabled()).provider(runtimeService.providerName())
                .agentCoreStatus(properties.isEnabled() ? "UP" : "DISABLED")
                .providerStatus(resolveProviderStatus(providerStatus))
                .ragEnabled(properties.isRagEnabled()).memoryEnabled(properties.isMemoryEnabled())
                .runningTasks(taskRepository.countByStatus(AgentTaskStatus.RUNNING))
                .checkedAt(LocalDateTime.now())
                .deepseekEnabled(providerStatus.isDeepseekEnabled())
                .deepseekConfigured(providerStatus.isDeepseekConfigured())
                .ollamaEnabled(providerStatus.isOllamaEnabled())
                .ollamaConfigured(providerStatus.isOllamaConfigured())
                .deepseekStatus(providerStatus.getDeepseekStatus())
                .deepseekModel(properties.getDeepseek().getModel())
                .ollamaStatus(providerStatus.getOllamaStatus())
                .ollamaModel(properties.getOllama().getModel())
                .deepseekCircuitState(providerStatus.getDeepseekCircuitState())
                .ollamaCircuitState(providerStatus.getOllamaCircuitState())
                .embeddingEnabled(embeddingStatus.isEnabled())
                .embeddingProvider(embeddingStatus.getProvider())
                .embeddingConfigured(embeddingStatus.isConfigured())
                .embeddingReady(embeddingStatus.isReady())
                .embeddingModel(embeddingStatus.getModel())
                .embeddingModelVersion(embeddingStatus.getModelVersion())
                .embeddingDimension(embeddingStatus.getDimension())
                .embeddingStatus(embeddingStatus.getStatus())
                .embeddingFallback(embeddingStatus.isFallback())
                .backendFactsEnabled(backendFactsStatus.enabled())
                .backendFactsAvailable(backendFactsStatus.available())
                .backendFactsStatus(backendFactsStatus.status())
                .projectDocumentsReady(documentSearchService.sectionCount() > 0)
                .projectDocumentSectionCount(documentSearchService.sectionCount())
                .webSearchConfigured(webSearchClient.configured())
                .marketDataToolAvailable(backendFactsStatus.available())
                .performanceToolAvailable(backendFactsStatus.available())
                .build();
    }

    /**
     * 执行 Agent Provider 健康检查。
     *
     * @return Provider 健康状态
     */
    public AgentHealthResponse healthCheck() {
        var health = runtimeService.providerHealthCheck();
        EmbeddingRuntimeStatus embeddingStatus = embeddingService.status();
        FactSourceStatus backendFactsStatus = factSourceStatusPort.status();
        return AgentHealthResponse.builder()
                .agentCoreStatus(properties.isEnabled() ? "UP" : "DISABLED")
                .providerStatus(resolveHealthProviderStatus(health.isDeepseekAvailable(), health.isOllamaAvailable()))
                .embeddingStatus(embeddingStatus.getStatus())
                .backendFactsStatus(backendFactsStatus.status())
                .deepseekAvailable(health.isDeepseekAvailable())
                .ollamaAvailable(health.isOllamaAvailable())
                .backendFactsEnabled(backendFactsStatus.enabled())
                .backendFactsAvailable(backendFactsStatus.available())
                .build();
    }

    private String resolveProviderStatus(AgentModelProviderStatus status) {
        String provider = runtimeService.providerName();
        return switch (provider) {
            case "deepseek" -> status.getDeepseekStatus();
            case "ollama" -> status.getOllamaStatus();
            case "auto" -> "AUTO";
            case "disabled" -> "DISABLED";
            default -> "UNKNOWN";
        };
    }

    private String resolveHealthProviderStatus(boolean deepseekAvailable, boolean ollamaAvailable) {
        if (!properties.isEnabled()) {
            return "DISABLED";
        }
        return deepseekAvailable || ollamaAvailable ? "AVAILABLE" : "UNAVAILABLE";
    }

    /**
     * 切换 Agent Provider。
     *
     * @param request Provider 切换请求
     * @return 当前 Provider 模式
     * @throws IllegalStateException 目标 Provider 不可用时抛出
     */
    public ProviderModeResponse switchProvider(ProviderModeRequest request) {
        String provider = request == null ? null : request.getProvider();
        if ("deepseek".equalsIgnoreCase(provider) || "ollama".equalsIgnoreCase(provider)) {
            boolean available = runtimeService.providerHealthCheck(provider.toLowerCase());
            if (!available) {
                throw new IllegalStateException("目标 Provider 当前不可用: " + provider);
            }
        }
        runtimeService.switchProvider(provider);
        return ProviderModeResponse.builder().provider(runtimeService.providerName()).build();
    }
}
// AI_GENERATE_END----
