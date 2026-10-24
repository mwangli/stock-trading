// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.time.LocalDateTime;

/**
 * Backend 交易决策新闻 HTTP 响应项。
 *
 * @param evidenceRef 新闻证据编号
 * @param title 新闻标题
 * @param source 新闻来源
 * @param category 新闻类别
 * @param eventType 结构化事件类型
 * @param publishTime 发布时间
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendTradeDecisionNewsResponse(
        String evidenceRef, String title, String source, String category,
        String eventType, LocalDateTime publishTime) {
}
// AI_GENERATE_END --
