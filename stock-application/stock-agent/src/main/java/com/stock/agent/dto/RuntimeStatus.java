// AI_GENERATE_START-----
package com.stock.agent.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 运行状态。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeStatus {
    /** Agent 是否启用。 */
    private boolean enabled;
    /** 当前模型提供方。 */
    private String provider;
    /** RAG 是否启用。 */
    private boolean ragEnabled;
    /** Memory 是否启用。 */
    private boolean memoryEnabled;
    /** 当前运行任务数。 */
    private long runningTasks;
    /** 当前时间。 */
    private LocalDateTime checkedAt;
    /** DeepSeek 是否启用。 */
    private boolean deepseekEnabled;
    /** DeepSeek 是否完成必要配置。 */
    private boolean deepseekConfigured;
    /** Ollama 是否启用。 */
    private boolean ollamaEnabled;
    /** Ollama 是否完成必要配置。 */
    private boolean ollamaConfigured;
    /** DeepSeek 最近一次运行状态。 */
    private String deepseekStatus;
    /** DeepSeek 当前配置的模型标识。 */
    private String deepseekModel;
    /** Ollama 最近一次运行状态。 */
    private String ollamaStatus;
    /** Ollama 当前配置的模型标识。 */
    private String ollamaModel;
    /** DeepSeek 熔断状态。 */
    private String deepseekCircuitState;
    /** Ollama 熔断状态。 */
    private String ollamaCircuitState;
    /** Embedding 是否启用。 */
    private boolean embeddingEnabled;
    /** Embedding Provider。 */
    private String embeddingProvider;
    /** Embedding 配置是否完整。 */
    private boolean embeddingConfigured;
    /** Embedding Bean 是否可用。 */
    private boolean embeddingReady;
    /** Embedding 模型名称。 */
    private String embeddingModel;
    /** Embedding 模型版本。 */
    private String embeddingModelVersion;
    /** Embedding 向量维度。 */
    private int embeddingDimension;
    /** Embedding 当前状态。 */
    private String embeddingStatus;
    /** 是否使用 hash 降级。 */
    private boolean embeddingFallback;
}
// AI_GENERATE_END-----
