// AI_GENERATE_START -
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 行情详情中的股票新闻摘要。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockNewsSummaryResponse {

    /** 新闻记录标识，不可为空。 */
    private String id;

    /** 新闻标题，不可为空。 */
    private String title;

    /** 新闻正文摘要，最多 500 个字符，可为空。 */
    private String summary;

    /** 新闻来源，可为空。 */
    private String source;

    /** 新闻分类，可为空。 */
    private String category;

    /** 新闻发布时间，可为空。 */
    private LocalDateTime publishTime;
}
// AI_GENERATE_END -