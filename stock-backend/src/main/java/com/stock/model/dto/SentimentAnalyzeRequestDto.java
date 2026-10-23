// AI_GENERATE_START------
package com.stock.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感分析单条文本请求 DTO
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentAnalyzeRequestDto {

    /**
     * 需要进行情感分析的文本内容（新闻、公告等）
     */
    private String text;
}


// AI_GENERATE_END------
