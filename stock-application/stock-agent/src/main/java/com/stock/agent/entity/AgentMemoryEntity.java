// AI_GENERATE_START------
package com.stock.agent.entity;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

import com.stock.agent.domain.AgentEnums;
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
@Document(collection = "agent_memory")
@Accessors(chain = true)
public class AgentMemoryEntity {

    /** 数据库主键。 */
    @Id
    private Long id;

    /** 业务记忆唯一标识。 */
    private String memoryId;

    /** 用户或账户范围。 */
    private String userScope;

    /** 记忆类型。 */
    private AgentEnums.MemoryType memoryType;

    /** 记忆来源类型。 */
    private AgentEnums.MemorySourceType sourceType;

    /** 记忆内容。 */
    private String content;

    /** 记忆来源标识。 */
    private String sourceRef;

    /** 记忆置信度，范围 0 到 1。 */
    private Double confidence;

    /** 记忆有效期。 */
    private LocalDateTime expiresAt;

    /** 记忆状态。 */
    private AgentEnums.MemoryStatus status;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END------
