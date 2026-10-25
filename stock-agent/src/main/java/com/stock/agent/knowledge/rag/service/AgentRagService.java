// AI_GENERATE_START----------------
package com.stock.agent.knowledge.rag.service;

import com.stock.agent.knowledge.rag.entity.AgentRagChunkDocument;

import com.stock.agent.knowledge.rag.dto.RagIngestRequest;
import com.stock.agent.knowledge.rag.dto.RagResult;
import com.stock.agent.knowledge.rag.dto.RagSearchRequest;
import com.stock.agent.knowledge.rag.embedding.LangChainEmbeddingService;
import com.stock.agent.knowledge.rag.source.BusinessRagSourceDocument;
import com.stock.agent.knowledge.rag.source.RagSourceSyncResult;
import com.stock.agent.shared.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * MongoDB RAG 文档摄取和检索服务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class AgentRagService {

    private final MongoTemplate mongoTemplate;
    private final AgentProperties properties;
    private final LangChainEmbeddingService embeddingService;

    /**
     * 将文档切分为有限长度 Chunk 并写入 MongoDB。
     *
     * @param request 文档请求
     * @param userScope 服务端认证用户范围
     * @return 写入的 Chunk 数量
     */
    public int ingest(RagIngestRequest request, String userScope) {
        requireEnabled();
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("RAG 文档内容不能为空");
        }
        String sourceId = StringUtils.hasText(request.getSourceId()) ? request.getSourceId() : UUID.randomUUID().toString();
        String version = StringUtils.hasText(request.getVersion()) ? request.getVersion() : "v1";
        List<String> chunks = split(request.getContent(), 1200);
        String sourceContentHash = hash(request.getContent());
        for (int index = 0; index < chunks.size(); index++) {
            String content = chunks.get(index);
            AgentRagChunkDocument document = new AgentRagChunkDocument();
            document.setChunkId(sourceId + "-" + version + "-" + index);
            document.setSourceType(defaultValue(request.getSourceType(), "DOCUMENT"));
            document.setUserScope(requireUserScope(userScope));
            document.setSourceId(sourceId);
            document.setVersion(version);
            document.setContent(content);
            document.setStockCodes(request.getStockCodes());
            document.setTags(request.getTags());
            document.setVisibility(defaultValue(request.getVisibility(), "PRIVATE"));
            document.setContentHash(hash(content));
            document.setSourceContentHash(sourceContentHash);
            document.setEmbeddingModelVersion(embeddingService.modelVersion());
            document.setEmbeddingProvider(embeddingService.provider());
            document.setEmbedding(embeddingService.embed(content));
            document.setEmbeddingDimension(embeddingService.dimension());
            document.setEmbeddingStatus(embeddingService.status().getStatus());
            document.setActive(true);
            document.setAsOf(LocalDateTime.now());
            document.setCreatedAt(LocalDateTime.now());
            mongoTemplate.save(document, properties.getRagCollection());
        }
        return chunks.size();
    }

    /**
     * 将业务系统标准文档按内容哈希幂等同步到 RAG。
     *
     * 该方法先完成全部 Chunk 的向量生成，再写入不可变新版本；新版本激活成功后才停用旧版本，
     * 避免单个向量失败破坏已有可检索知识。
     *
     * @param source 业务来源标准文档
     * @return 单来源同步结果
     */
    public RagSourceSyncResult synchronizeBusinessSource(BusinessRagSourceDocument source) {
        requireEnabled();
        requireReadyEmbedding();
        validateBusinessSource(source);

        String userScope = requireUserScope(source.userScope());
        String sourceType = source.sourceType().trim();
        String sourceId = source.sourceId().trim();
        String sourceContentHash = hash(source.content());
        String contractHash = hash(sourceContentHash + "|" + embeddingService.provider() + "|"
                + embeddingService.modelVersion() + "|" + embeddingService.dimension());
        String version = "auto-" + contractHash.substring(0, 16);

        Query currentQuery = Query.query(sourceCriteria(sourceType, sourceId, userScope)
                .and("sourceContentHash").is(sourceContentHash)
                .and("version").is(version)
                .and("active").is(true)
                .and("embeddingModelVersion").is(embeddingService.modelVersion())
                .and("embeddingProvider").is(embeddingService.provider())
                .and("embeddingDimension").is(embeddingService.dimension())
                .and("embeddingStatus").is("READY"));
        long currentChunks = mongoTemplate.count(currentQuery, properties.getRagCollection());
        if (currentChunks > 0) {
            deactivateOtherVersions(sourceType, sourceId, userScope, version);
            return new RagSourceSyncResult(sourceType, sourceId, version, Math.toIntExact(currentChunks), false);
        }

        List<String> chunks = split(source.content(), 1200);
        LocalDateTime now = LocalDateTime.now();
        List<AgentRagChunkDocument> documents = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            String content = chunks.get(index);
            AgentRagChunkDocument document = new AgentRagChunkDocument();
            document.setChunkId(businessChunkId(sourceType, sourceId, userScope, version, index));
            document.setSourceType(sourceType);
            document.setUserScope(userScope);
            document.setSourceId(sourceId);
            document.setVersion(version);
            document.setContent(content);
            document.setStockCodes(source.stockCodes());
            document.setTags(source.tags());
            document.setVisibility(defaultValue(source.visibility(), "SHARED"));
            document.setContentHash(hash(content));
            document.setSourceContentHash(sourceContentHash);
            document.setEmbeddingModelVersion(embeddingService.modelVersion());
            document.setEmbeddingProvider(embeddingService.provider());
            document.setEmbedding(embeddingService.embed(content));
            document.setEmbeddingDimension(embeddingService.dimension());
            document.setEmbeddingStatus("READY");
            document.setActive(false);
            document.setAsOf(source.asOf() == null ? now : source.asOf());
            document.setCreatedAt(now);
            documents.add(document);
        }

        Query newVersionQuery = Query.query(sourceCriteria(sourceType, sourceId, userScope)
                .and("version").is(version));
        mongoTemplate.remove(newVersionQuery, properties.getRagCollection());
        try {
            mongoTemplate.insert(documents, properties.getRagCollection());
            mongoTemplate.updateMulti(newVersionQuery, Update.update("active", true),
                    properties.getRagCollection());
            deactivateOtherVersions(sourceType, sourceId, userScope, version);
            return new RagSourceSyncResult(sourceType, sourceId, version, documents.size(), true);
        } catch (RuntimeException exception) {
            try {
                mongoTemplate.remove(newVersionQuery, properties.getRagCollection());
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new IllegalStateException("业务知识写入 RAG 失败: sourceType=" + sourceType
                    + ", sourceId=" + sourceId, exception);
        }
    }

    /**
     * 从 MongoDB 检索候选 Chunk，并在应用层计算轻量向量相似度。
     *
     * @param request 检索请求
     * @param userScope 服务端认证用户范围
     * @return 按相似度降序排列的结果
     */
    public List<RagResult> search(RagSearchRequest request, String userScope) {
        requireEnabled();
        if (request == null || !StringUtils.hasText(request.getQuery())) {
            return List.of();
        }
        int limit = Math.min(Math.max(request.getLimit() == null ? 5 : request.getLimit(), 1), 20);
        Criteria criteria = Criteria.where("active").is(true);
        if (StringUtils.hasText(request.getSourceType())) {
            criteria = criteria.and("sourceType").is(request.getSourceType().trim());
        }
        if (StringUtils.hasText(request.getVersion())) {
            criteria = criteria.and("version").is(request.getVersion().trim());
        }
        if (request.getAsOfFrom() != null && request.getAsOfTo() != null
                && request.getAsOfFrom().isAfter(request.getAsOfTo())) {
            throw new IllegalArgumentException("RAG 时间范围无效");
        }
        if (request.getAsOfFrom() != null) {
            criteria = criteria.and("asOf").gte(request.getAsOfFrom());
        }
        if (request.getAsOfTo() != null) {
            criteria = criteria.and("asOf").lte(request.getAsOfTo());
        }
        criteria = criteria.and("embeddingModelVersion").is(embeddingService.modelVersion())
                .and("embeddingProvider").is(embeddingService.provider())
                .and("embeddingDimension").is(embeddingService.dimension())
                .and("embeddingStatus").is("READY");
        Query query = Query.query(criteria);
        List<AgentRagChunkDocument> documents = mongoTemplate.find(query, AgentRagChunkDocument.class,
                properties.getRagCollection());
        List<Double> queryVector = embeddingService.embed(request.getQuery());
        return documents.stream()
                .filter(item -> visible(item, requireUserScope(userScope)))
                .map(item -> RagResult.builder()
                        .chunkId(item.getChunkId())
                        .sourceType(item.getSourceType())
                        .sourceId(item.getSourceId())
                        .version(item.getVersion())
                        .content(item.getContent())
                        .score(cosine(queryVector, item.getEmbedding()))
                        .asOf(item.getAsOf())
                        .build())
                .sorted(Comparator.comparing(RagResult::getScore).reversed())
                .limit(limit)
                .toList();
    }

    private boolean visible(AgentRagChunkDocument item, String userScope) {
        if (!"PRIVATE".equalsIgnoreCase(item.getVisibility())) {
            return true;
        }
        return StringUtils.hasText(userScope)
                && userScope.trim().equals(item.getUserScope());
    }

    private void requireReadyEmbedding() {
        if (!embeddingService.status().isReady()) {
            throw new IllegalStateException("EMBEDDING_NOT_READY");
        }
    }

    private void validateBusinessSource(BusinessRagSourceDocument source) {
        if (source == null || !StringUtils.hasText(source.sourceType())
                || !StringUtils.hasText(source.sourceId()) || !StringUtils.hasText(source.content())) {
            throw new IllegalArgumentException("RAG 业务来源类型、标识和正文不能为空");
        }
    }

    private Criteria sourceCriteria(String sourceType, String sourceId, String userScope) {
        return Criteria.where("sourceType").is(sourceType)
                .and("sourceId").is(sourceId)
                .and("userScope").is(userScope);
    }

    private void deactivateOtherVersions(String sourceType, String sourceId,
                                         String userScope, String currentVersion) {
        Query oldVersions = Query.query(sourceCriteria(sourceType, sourceId, userScope)
                .and("version").ne(currentVersion)
                .and("active").is(true));
        mongoTemplate.updateMulti(oldVersions, Update.update("active", false), properties.getRagCollection());
    }

    private String businessChunkId(String sourceType, String sourceId, String userScope,
                                   String version, int index) {
        return "business-" + hash(sourceType + "|" + sourceId + "|" + userScope)
                .substring(0, 24) + "-" + version + "-" + index;
    }

    private void requireEnabled() {
        if (!properties.isRagEnabled()) {
            throw new IllegalStateException("RAG 功能当前未启用");
        }
    }

    private List<String> split(String content, int maxLength) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < content.length(); start += maxLength) {
            chunks.add(content.substring(start, Math.min(content.length(), start + maxLength)));
        }
        return chunks;
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (right == null || right.isEmpty()) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        int size = Math.min(left.size(), right.size());
        for (int index = 0; index < size; index++) {
            double leftValue = left.get(index);
            double rightValue = right.get(index);
            dot += leftValue * rightValue;
            leftNorm += leftValue * leftValue;
            rightNorm += rightValue * rightValue;
        }
        return leftNorm == 0D || rightNorm == 0D ? 0D : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private String hash(String content) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("计算 RAG 内容哈希失败", exception);
        }
    }

    private String defaultValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String requireUserScope(String userScope) {
        if (!StringUtils.hasText(userScope)) {
            throw new IllegalArgumentException("RAG 用户范围不能为空");
        }
        return userScope.trim();
    }
}
// AI_GENERATE_END---------------
