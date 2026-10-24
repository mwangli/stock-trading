// AI_GENERATE_START--
package com.stock.agent.interaction.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 当前用户的 Agent 会话列表响应。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Value
@Builder
public class SessionListResponse {

    /** 当前用户的会话列表，按最近更新时间倒序排列。 */
    List<SessionResponse> items;
}
// AI_GENERATE_END-
