// AI_GENERATE_START-----------------
package com.stock.agent.service;

import com.stock.agent.client.DjlEmbeddingModel;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.model.EmbeddingRuntimeStatus;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LangChain4j Embedding 适配服务。
 * 未配置 EmbeddingModel 时默认失败即停止，只有显式开启才允许使用本地降级向量。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class LangChainEmbeddingService {

    private static final int FALLBACK_EMBEDDING_SIZE = 32;

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final AgentProperties properties;

    /**
     * 生成文本向量。
     *
     * @param content 文本
     * @return double 向量
     */
    public List<Double> embed(String content) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model != null) {
            List<Double> vector = model.embed(content).content().vectorAsList().stream()
                    .map(Number::doubleValue)
                    .toList();
            validateDimension(vector);
            return vector;
        }
        if (!properties.getEmbedding().isAllowHashFallback()) {
            throw new IllegalStateException("EMBEDDING_NOT_READY");
        }
        return fallbackEmbed(content);
    }

    /**
     * 返回当前 Embedding 模型版本。
     *
     * @return 模型版本
     */
    public String modelVersion() {
        AgentProperties.EmbeddingProperties embedding = properties.getEmbedding();
        if (embedding.getModelVersion() != null && !embedding.getModelVersion().isBlank()) {
            return embedding.getModelVersion();
        }
        return embedding.isAllowHashFallback() ? "hash-32-v1" : "unconfigured";
    }

    /**
     * 返回当前 Embedding Provider 状态。
     *
     * @return 脱敏运行状态
     */
    public EmbeddingRuntimeStatus status() {
        AgentProperties.EmbeddingProperties embedding = properties.getEmbedding();
        String provider = embedding.getProvider() == null ? "disabled" : embedding.getProvider();
        boolean configured = embedding.isEnabled() && isProviderConfigured(embedding, provider);
        boolean ready = false;
        String message = null;
        if (configured) {
            try {
                EmbeddingModel model = embeddingModelProvider.getIfAvailable();
                if (model instanceof DjlEmbeddingModel djlModel) {
                    ready = djlModel.isReady();
                    message = djlModel.lastLoadError();
                } else {
                    ready = model != null;
                }
            } catch (RuntimeException exception) {
                ready = false;
                message = exception.getMessage();
            }
        }
        boolean fallback = !ready && embedding.isAllowHashFallback();
        String status = !embedding.isEnabled() || "disabled".equalsIgnoreCase(provider)
                ? "DISABLED" : fallback ? "HASH_FALLBACK" : ready ? "READY"
                : message == null ? "INITIALIZING" : "UNAVAILABLE";
        return EmbeddingRuntimeStatus.builder()
                .enabled(embedding.isEnabled())
                .provider(provider)
                .configured(configured)
                .ready(ready)
                .model(embedding.getModel())
                .modelVersion(modelVersion())
                .dimension(embedding.getDimension())
                .status(status)
                .message(message)
                .fallback(fallback)
                .build();
    }

    /**
     * 返回当前向量维度。
     *
     * @return 向量维度
     */
    public int dimension() {
        return properties.getEmbedding().isAllowHashFallback()
                && embeddingModelProvider.getIfAvailable() == null ? FALLBACK_EMBEDDING_SIZE
                : properties.getEmbedding().getDimension();
    }

    /**
     * 返回当前 Provider 名称。
     *
     * @return Provider 名称
     */
    public String provider() {
        return properties.getEmbedding().getProvider();
    }

    private List<Double> fallbackEmbed(String content) {
        List<Double> vector = new ArrayList<>();
        byte[] bytes = content.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < FALLBACK_EMBEDDING_SIZE; index++) {
            double value = 0;
            for (int offset = index; offset < bytes.length; offset += FALLBACK_EMBEDDING_SIZE) {
                value += bytes[offset] * (offset + 1L);
            }
            vector.add(value / Math.max(bytes.length, 1));
        }
        return vector;
    }

    private void validateDimension(List<Double> vector) {
        int expected = properties.getEmbedding().getDimension();
        if (expected > 0 && vector.size() != expected) {
            throw new IllegalStateException("EMBEDDING_DIMENSION_MISMATCH: expected="
                    + expected + ", actual=" + vector.size());
        }
    }

    private boolean content(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isProviderConfigured(AgentProperties.EmbeddingProperties embedding, String provider) {
        if (!content(embedding.getModel())) {
            return false;
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "djl" -> content(embedding.getModelVersion()) && content(embedding.getModelPath());
            case "ollama" -> content(embedding.getBaseUrl());
            case "openaicompatible" -> content(embedding.getBaseUrl()) && content(embedding.getApiKey());
            default -> false;
        };
    }
}
// AI_GENERATE_END-----------------