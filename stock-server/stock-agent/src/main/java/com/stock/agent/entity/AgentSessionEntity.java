// AI_GENERATE_START--------
package com.stock.agent.entity;

import com.stock.agent.enums.AgentSessionStatus;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

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
@Document(collection = "agent_session")
@Accessors(chain = true)
public class AgentSessionEntity {

    /** 会话唯一标识。 */
    @Id
    private String sessionId;

    /** 用户或账户范围标识。 */
    private String userScope;

    /** 会话标题。 */
    private String title;

    /** 会话状态。 */
    private AgentSessionStatus status;

    /** 会话创建时间。 */
    private LocalDateTime createdAt;

    /** 会话更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END-------