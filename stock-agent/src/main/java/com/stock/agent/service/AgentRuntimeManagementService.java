// AI_GENERATE_START---
package com.stock.agent.service;

import com.stock.agent.config.AgentProperties;
import com.stock.agent.domain.AgentEnums;
import com.stock.agent.dto.AgentHealthResponse;
import com.stock.agent.dto.ProviderModeRequest;
import com.stock.agent.dto.ProviderModeResponse;
import com.stock.agent.dto.RuntimeStatus;
import com.stock.agent.rag.EmbeddingRuntimeStatus;
import com.stock.agent.rag.service.LangChainEmbeddingService;
import com.stock.agent.repository.AgentTaskRepository;
import com.stock.agent.runtime.AgentRuntimeCoordinator;
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

    /**
     * 获取 Agent 运行状态。
     *
     * @return 运行状态
     */
    public RuntimeStatus runtimeStatus() {
        var providerStatus = runtimeService.providerStatus();
        EmbeddingRuntimeStatus embeddingStatus = embeddingService.status();
        return RuntimeStatus.builder()
                .enabled(properties.isEnabled()).provider(runtimeService.providerName())
                .ragEnabled(properties.isRagEnabled()).memoryEnabled(properties.isMemoryEnabled())
                .runningTasks(taskRepository.countByStatus(AgentEnums.TaskStatus.RUNNING))
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
                .build();
    }

    /**
     * 执行 Agent Provider 健康检查。
     *
     * @return Provider 健康状态
     */
    public AgentHealthResponse healthCheck() {
        var health = runtimeService.providerHealthCheck();
        return AgentHealthResponse.builder()
                .deepseekAvailable(health.isDeepseekAvailable())
                .ollamaAvailable(health.isOllamaAvailable())
                .build();
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
// AI_GENERATE_END---
