// AI_GENERATE_START---
package com.stock.agent.decision.vo;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 交易决策上下文中的只读新闻摘要。
 * 仅保存信息截止时间前的有限元数据，不向模型暴露完整新闻正文。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionNewsSnapshot {

    /** 新闻证据编号。 */
    String evidenceRef;

    /** 新闻标题。 */
    String title;

    /** 新闻来源，可为空。 */
    String source;

    /** 新闻或公告类别，可为空。 */
    String category;

    /** 结构化事件类型，可为空。 */
    String eventType;

    /** 新闻发布时间。 */
    LocalDateTime publishTime;
}
// AI_GENERATE_END---
