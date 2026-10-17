// AI_GENERATE_START -
package com.stock.strategyAgent.rag;

import com.stock.strategyAgent.config.AgentProperties;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * LangChain4j Embedding 适配服务。
 * 未配置 EmbeddingModel 时使用确定性的本地降级向量，避免 RAG 直接伪造实时事实。
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
            return model.embed(content).content().vectorAsList().stream()
                    .map(Number::doubleValue)
                    .toList();
        }
        return fallbackEmbed(content);
    }

    /**
     * 返回当前 Embedding 模型版本。
     *
     * @return 模型版本
     */
    public String modelVersion() {
        return properties.getEmbeddingModelVersion().isBlank()
                ? "hash-32-v1" : properties.getEmbeddingModelVersion();
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
}
// AI_GENERATE_END -
