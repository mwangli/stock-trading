// AI_GENERATE_START -
package com.stock.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 跨业务消费的新闻证据摘要。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketNewsDto {
    /** 新闻记录标识。 */
    private String id;
    /** 外部来源标识。 */
    private String externalId;
    /** 新闻标题。 */
    private String title;
    /** 新闻来源。 */
    private String source;
    /** 新闻分类。 */
    private String category;
    /** 事件类型。 */
    private String eventType;
    /** 内容哈希。 */
    private String contentHash;
    /** 发布时间。 */
    private LocalDateTime publishTime;
}
// AI_GENERATE_END -
