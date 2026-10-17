// AI_GENERATE_START --
package com.stock.strategyAgent.domain.entity;

import com.stock.strategyAgent.domain.AgentEnums;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 长期记忆元数据。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Entity
@Table(name = "agent_memory", indexes = {
        @Index(name = "idx_agent_memory_scope_status", columnList = "user_scope,status"),
        @Index(name = "idx_agent_memory_expire", columnList = "expires_at")
})
@Accessors(chain = true)
public class AgentMemoryEntity {

    /** 数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 业务记忆唯一标识。 */
    @Column(name = "memory_id", nullable = false, unique = true, length = 64)
    private String memoryId;

    /** 用户或账户范围。 */
    @Column(name = "user_scope", nullable = false, length = 128)
    private String userScope;

    /** 记忆类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type", nullable = false, length = 32)
    private AgentEnums.MemoryType memoryType;

    /** 记忆来源类型。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private AgentEnums.MemorySourceType sourceType;

    /** 记忆内容。 */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 记忆来源标识。 */
    @Column(name = "source_ref", length = 256)
    private String sourceRef;

    /** 记忆置信度，范围 0 到 1。 */
    @Column(nullable = false)
    private Double confidence;

    /** 记忆有效期。 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 记忆状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentEnums.MemoryStatus status;

    /** 创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END --
