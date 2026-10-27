// AI_GENERATE_START-----------------
package com.stock.agent.service;

import com.stock.agent.enums.MemoryStatus;

import com.stock.agent.enums.MemorySourceType;

import com.stock.agent.dto.CorrectMemoryRequest;
import com.stock.agent.dto.CreateMemoryRequest;
import com.stock.agent.dto.MemoryResponse;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.entity.AgentMemoryEntity;
import com.stock.agent.repository.AgentMemoryRepository;
import com.stock.agent.entity.AgentMemoryVectorDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agent Memory 生命周期服务。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class AgentMemoryService {

    private final AgentMemoryRepository repository;
    private final MongoTemplate mongoTemplate;
    private final AgentProperties properties;
    private final LangChainEmbeddingService embeddingService;

    /**
     * 创建一条经过字段和敏感信息校验的记忆。
     *
     * @param request 创建请求
     * @param userScope 服务端认证用户范围
     * @return 记忆响应
     */
    public MemoryResponse create(CreateMemoryRequest request, String userScope) {
        validate(request);
        LocalDateTime now = LocalDateTime.now();
        int ttl = Math.min(Math.max(request.getTtlDays() == null ? properties.getMemoryDefaultTtlDays() : request.getTtlDays(), 1), 3650);
        AgentMemoryEntity entity = new AgentMemoryEntity()
                .setMemoryId(UUID.randomUUID().toString())
                .setUserScope(requireUserScope(userScope))
                .setMemoryType(request.getMemoryType())
                .setSourceType(request.getSourceType())
                .setContent(redact(request.getContent()))
                .setSourceRef(redact(request.getSourceRef()))
                .setConfidence(Math.min(Math.max(request.getConfidence() == null ? 0.5D : request.getConfidence(), 0D), 1D))
                .setExpiresAt(now.plusDays(ttl))
                .setStatus(MemoryStatus.ACTIVE)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        AgentMemoryEntity saved = repository.save(entity);
        AgentMemoryVectorDocument vector = new AgentMemoryVectorDocument();
        vector.setMemoryId(saved.getMemoryId());
        vector.setUserScope(saved.getUserScope());
        vector.setMemoryType(saved.getMemoryType().name());
        vector.setContent(saved.getContent());
        vector.setEmbedding(embeddingService.embed(saved.getContent()));
        vector.setEmbeddingModelVersion(embeddingService.modelVersion());
        vector.setEmbeddingProvider(embeddingService.provider());
        vector.setEmbeddingDimension(embeddingService.dimension());
        vector.setEmbeddingStatus(embeddingService.status().getStatus());
        vector.setActive(true);
        vector.setExpiresAt(saved.getExpiresAt());
        vector.setUpdatedAt(now);
        mongoTemplate.save(vector);
        return toResponse(saved);
    }

    /**
     * 查询用户有效记忆。
     *
     * @param userScope 用户范围
     * @return 记忆列表
     */
    public List<MemoryResponse> list(String userScope) {
        expireOldMemories();
        return repository.findByUserScopeAndStatusOrderByUpdatedAtDesc(requireUserScope(userScope), MemoryStatus.ACTIVE)
                .stream().map(this::toResponse).toList();
    }

    /**
     * 删除一条记忆并同步禁用 MongoDB 语义索引。
     *
     * @param memoryId 记忆标识
     * @param userScope 服务端认证用户范围
     */
    public void forget(String memoryId, String userScope) {
        AgentMemoryEntity entity = find(memoryId, userScope);
        entity.setStatus(MemoryStatus.FORGOTTEN).setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        AgentMemoryVectorDocument vector = mongoTemplate.findById(memoryId, AgentMemoryVectorDocument.class);
        if (vector != null) {
            vector.setActive(false);
            vector.setUpdatedAt(LocalDateTime.now());
            mongoTemplate.save(vector);
        }
    }

    /**
     * 修正一条记忆。
     *
     * @param memoryId 记忆标识
     * @param request 修正请求
     * @param userScope 服务端认证用户范围
     * @return 修正后的记忆
     */
    public MemoryResponse correct(String memoryId, CorrectMemoryRequest request, String userScope) {
        if (request == null || !StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("修正内容不能为空");
        }
        AgentMemoryEntity entity = find(memoryId, userScope);
        entity.setContent(redact(request.getContent()))
                .setSourceRef(redact(request.getSourceRef()))
                .setSourceType(MemorySourceType.USER_CONFIRMED)
                .setStatus(MemoryStatus.ACTIVE)
                .setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        AgentMemoryVectorDocument vector = mongoTemplate.findById(memoryId, AgentMemoryVectorDocument.class);
        if (vector != null) {
            vector.setContent(entity.getContent());
            vector.setEmbedding(embeddingService.embed(entity.getContent()));
            vector.setEmbeddingModelVersion(embeddingService.modelVersion());
            vector.setEmbeddingProvider(embeddingService.provider());
            vector.setEmbeddingDimension(embeddingService.dimension());
            vector.setEmbeddingStatus(embeddingService.status().getStatus());
            vector.setActive(true);
            vector.setUpdatedAt(entity.getUpdatedAt());
            mongoTemplate.save(vector);
        } else {
            AgentMemoryVectorDocument recreated = new AgentMemoryVectorDocument();
            recreated.setMemoryId(entity.getMemoryId());
            recreated.setUserScope(entity.getUserScope());
            recreated.setMemoryType(entity.getMemoryType().name());
            recreated.setContent(entity.getContent());
            recreated.setEmbedding(embeddingService.embed(entity.getContent()));
            recreated.setEmbeddingModelVersion(embeddingService.modelVersion());
            recreated.setEmbeddingProvider(embeddingService.provider());
            recreated.setEmbeddingDimension(embeddingService.dimension());
            recreated.setEmbeddingStatus(embeddingService.status().getStatus());
            recreated.setActive(true);
            recreated.setExpiresAt(entity.getExpiresAt());
            recreated.setUpdatedAt(entity.getUpdatedAt());
            mongoTemplate.save(recreated);
        }
        return toResponse(entity);
    }

    private AgentMemoryEntity find(String memoryId, String userScope) {
        return repository.findByMemoryIdAndUserScope(memoryId, requireUserScope(userScope))
                .orElseThrow(() -> new IllegalArgumentException("Memory 不存在"));
    }

    private void expireOldMemories() {
        repository.findAll().stream()
                .filter(item -> item.getStatus() == MemoryStatus.ACTIVE)
                .filter(item -> item.getExpiresAt() != null && item.getExpiresAt().isBefore(LocalDateTime.now()))
                .forEach(item -> repository.save(item.setStatus(MemoryStatus.EXPIRED).setUpdatedAt(LocalDateTime.now())));
    }

    private void validate(CreateMemoryRequest request) {
        if (!properties.isMemoryEnabled()) {
            throw new IllegalStateException("Memory 功能当前未启用");
        }
        if (request == null || request.getMemoryType() == null || request.getSourceType() == null
                || !StringUtils.hasText(request.getContent())) {
            throw new IllegalArgumentException("Memory 类型、来源类型和内容不能为空");
        }
        String lower = request.getContent().toLowerCase();
        if (lower.contains("password") || lower.contains("token") || lower.contains("cookie")
                || lower.contains("私钥") || lower.contains("密码")) {
            throw new IllegalArgumentException("Memory 内容包含禁止持久化的敏感字段");
        }
        if (request.getContent().length() > 4000) {
            throw new IllegalArgumentException("Memory 内容不能超过 4000 个字符");
        }
    }

    private String redact(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?i)(password|token|cookie|secret)\\s*[=:：]\\s*[^,;\\s]+", "$1=[REDACTED_SECRET]");
    }

    private String requireUserScope(String userScope) {
        if (!StringUtils.hasText(userScope)) {
            throw new IllegalArgumentException("Memory 用户范围不能为空");
        }
        return userScope.trim();
    }

    private MemoryResponse toResponse(AgentMemoryEntity entity) {
        return MemoryResponse.builder()
                .memoryId(entity.getMemoryId())
                .memoryType(entity.getMemoryType().name())
                .sourceType(entity.getSourceType().name())
                .content(entity.getContent())
                .sourceRef(entity.getSourceRef())
                .confidence(entity.getConfidence())
                .expiresAt(entity.getExpiresAt())
                .status(entity.getStatus().name())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
// AI_GENERATE_END----------------