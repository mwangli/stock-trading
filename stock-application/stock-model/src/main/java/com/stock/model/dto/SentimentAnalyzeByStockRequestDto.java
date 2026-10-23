// AI_GENERATE_START------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按股票代码进行情感分析的请求 DTO
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalyzeByStockRequestDto {

    /**
     * 股票代码，如 600519
     */
    private String stockCode;

    /**
     * 新闻或公告文本内容
     */
    private String text;
}


// AI_GENERATE_END------
