// AI_GENERATE_START---
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感分析随机新闻样本 DTO
 * 提供信息表中可直接用于单条情感验证的新闻或公告内容。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsSampleResponseDto {

    /**
     * 新闻标题
     */
    private String title;

    /**
     * 新闻正文内容
     */
    private String content;

    /**
     * 关联股票代码
     */
    private String stockCode;

    /**
     * 关联股票名称
     */
    private String stockName;

    /**
     * 新闻来源
     */
    private String source;

    /**
     * 信息类型，如新闻或公告
     */
    private String category;

    /**
     * 发布时间，使用 ISO-8601 格式返回
     */
    private String publishTime;
}
// AI_GENERATE_END---
