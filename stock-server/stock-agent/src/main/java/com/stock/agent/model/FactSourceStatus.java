// AI_GENERATE_START ---
package com.stock.agent.model;

/**
 * 外部权威事实源运行状态。
 *
 * @param enabled 是否启用事实源访问
 * @param available 最近探测是否可用
 * @param status 状态标识
 * @param message 脱敏状态说明
 * @author mwangli
 * @since 2026-10-24
 */
public record FactSourceStatus(boolean enabled, boolean available, String status, String message) {
}
// AI_GENERATE_END --