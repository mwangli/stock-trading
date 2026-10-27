// AI_GENERATE_START------
package com.stock.agent.dto;

import com.stock.agent.enums.AgentChatStatus;
import com.stock.agent.enums.AgentTaskStrategy;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 统一聊天响应。
 *
 * @author mwangli
 * @since 2026-10-22
 */
@Value
@Builder
public class AgentChatResponse {

    /** 当前用户会话标识。 */
    String sessionId;

    /** 本轮任务标识。 */
    String taskId;

    /** 面向用户的最终回答。 */
    String answer;

    /** 本轮后端任务策略。 */
    AgentTaskStrategy strategy;

    /** 本轮执行状态。 */
    AgentChatStatus status;

    /** 本轮结构化证据编号列表，不存在证据时为空列表。 */
    List<String> evidenceRefs;

    /** 本轮允许模型调用的工具名称列表。 */
    List<String> capabilities;

    /** 数据不足、冲突或降级原因，可为空。 */
    String uncertainty;

    /** 回答生成时间。 */
    LocalDateTime createdAt;
}
// AI_GENERATE_END-----
