// AI_GENERATE_START-------
package com.stock.agent.rag.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB RAG 文档分块。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Document(collection = "agent_rag_chunks")
@CompoundIndex(name = "idx_agent_rag_source_version", def = "{'sourceType': 1, 'sourceId': 1, 'version': 1}")
@CompoundIndex(name = "idx_agent_rag_active_visibility", def = "{'active': 1, 'visibility': 1}")
public class AgentRagChunkDocument {

    /** MongoDB 文档标识。 */
    @Id
    private String chunkId;

    /** 来源类型。 */
    private String sourceType;

    /** 文档所属用户范围。 */
    private String userScope;

    /** 来源标识。 */
    private String sourceId;

    /** 来源版本。 */
    private String version;

    /** 分块正文。 */
    private String content;

    /** 关联股票代码。 */
    private List<String> stockCodes;

    /** 文档标签。 */
    private List<String> tags;

    /** 可见范围。 */
    private String visibility;

    /** 内容哈希。 */
    private String contentHash;

    /** Embedding 模型版本。 */
    private String embeddingModelVersion;

    /** Embedding Provider。 */
    private String embeddingProvider;

    /** Embedding 向量维度。 */
    private Integer embeddingDimension;

    /** Embedding 生成状态。 */
    private String embeddingStatus;

    /** Embedding 向量。 */
    private List<Double> embedding;

    /** 是否可检索。 */
    private boolean active;

    /** 文档时间。 */
    private LocalDateTime asOf;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
// AI_GENERATE_END-------
