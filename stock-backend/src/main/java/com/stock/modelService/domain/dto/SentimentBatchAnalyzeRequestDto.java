package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 情感分析批量文本请求 DTO
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentBatchAnalyzeRequestDto {

    /**
     * 需要进行情感分析的文本列表
     */
    private List<String> texts;
}

