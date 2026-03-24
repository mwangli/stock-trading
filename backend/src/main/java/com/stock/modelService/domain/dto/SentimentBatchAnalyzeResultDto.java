package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量情感分析结果 DTO
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentBatchAnalyzeResultDto {

    /**
     * 是否调用成功
     */
    private boolean success;

    /**
     * 提示信息（错误信息或说明）
     */
    private String message;

    /**
     * 结果数量
     */
    private int count;

    /**
     * 每条文本的情感分析结果列表
     */
    private List<SentimentAnalyzeResultDto> results;
}

