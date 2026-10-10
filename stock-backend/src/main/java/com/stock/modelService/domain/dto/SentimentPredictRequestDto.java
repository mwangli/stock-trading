package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 情感分析模型预测请求 DTO
 * <p>
 * 用于输入股票代码和新闻内容，调用情感分析模型进行预测。
 * </p>
 *
 * @author mwangli
 * @since 2026-03-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentPredictRequestDto {

    /**
     * 股票代码，如 600519、000001
     */
    private String stockCode;

    /**
     * 新闻或公告文本内容，待分析的情感对象
     */
    private String news;
}
