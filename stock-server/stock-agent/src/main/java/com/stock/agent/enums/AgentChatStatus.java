// AI_GENERATE_START------
package com.stock.agent.enums;

/**
 * Agent 统一聊天响应状态。
 *
 * @author mwangli
 * @since 2026-10-22
 */
public enum AgentChatStatus {

    /** 聊天任务正常完成。 */
    COMPLETED,

    /** 异步任务已受理。 */
    ACCEPTED,

    /** 聊天任务降级完成。 */
    DEGRADED,

    /** 当前请求需要用户确认后才能继续。 */
    NEEDS_CONFIRMATION,

    /** 聊天任务执行失败。 */
    FAILED
}
// AI_GENERATE_END-----