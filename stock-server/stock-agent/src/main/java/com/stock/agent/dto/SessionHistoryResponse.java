// AI_GENERATE_START---
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Agent 会话历史响应。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Value
@Builder
public class SessionHistoryResponse {

    /** 会话基本信息。 */
    SessionResponse session;

    /** 会话任务列表，按创建时间正序排列。 */
    List<TaskResponse> tasks;
}
// AI_GENERATE_END--