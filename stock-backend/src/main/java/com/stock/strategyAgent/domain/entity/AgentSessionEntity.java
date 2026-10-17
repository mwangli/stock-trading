// AI_GENERATE_START --
package com.stock.strategyAgent.domain.entity;

import com.stock.strategyAgent.domain.AgentEnums;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * Agent 会话实体。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Entity
@Table(name = "agent_session")
@Accessors(chain = true)
public class AgentSessionEntity {

    /** 会话唯一标识。 */
    @Id
    @Column(name = "session_id", length = 64)
    private String sessionId;

    /** 用户或账户范围标识。 */
    @Column(name = "user_scope", nullable = false, length = 128)
    private String userScope;

    /** 会话标题。 */
    @Column(length = 256)
    private String title;

    /** 会话状态。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentEnums.SessionStatus status;

    /** 会话创建时间。 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 会话更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END --
