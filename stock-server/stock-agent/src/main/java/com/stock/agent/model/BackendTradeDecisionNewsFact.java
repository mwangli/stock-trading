// AI_GENERATE_START ---
package com.stock.agent.model;

import java.time.LocalDateTime;

/**
 * Backend 固化的交易决策新闻摘要。
 *
 * @param evidenceRef 新闻证据编号
 * @param title 新闻标题
 * @param source 新闻来源
 * @param category 新闻类别
 * @param eventType 结构化事件类型
 * @param publishTime 发布时间
 * @author mwangli
 * @since 2026-10-23
 */
public record BackendTradeDecisionNewsFact(
        String evidenceRef, String title, String source, String category,
        String eventType, LocalDateTime publishTime) {
}
// AI_GENERATE_END --