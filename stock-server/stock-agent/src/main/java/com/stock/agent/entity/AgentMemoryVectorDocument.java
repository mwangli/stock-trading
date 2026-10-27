// AI_GENERATE_START---------
package com.stock.agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB Memory 语义索引文档。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Document(collection = "agent_memory_vectors")
public class AgentMemoryVectorDocument {

    /** Memory 业务标识。 */
    @Id
    private String memoryId;

    /** 用户范围。 */
    private String userScope;

    /** Memory 类型。 */
    private String memoryType;

    /** 记忆内容摘要。 */
    private String content;

    /** 语义向量。 */
    private List<Double> embedding;

    /** Embedding 模型版本。 */
    private String embeddingModelVersion;

    /** Embedding Provider。 */
    private String embeddingProvider;

    /** Embedding 向量维度。 */
    private Integer embeddingDimension;

    /** Embedding 生成状态。 */
    private String embeddingStatus;

    /** 是否可检索。 */
    private boolean active;

    /** 过期时间。 */
    private LocalDateTime expiresAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END--------