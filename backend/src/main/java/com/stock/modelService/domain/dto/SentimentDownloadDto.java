package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 预训练情感模型下载结果 DTO
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentDownloadDto {

    private boolean success;

    private String message;

    private String modelPath;
}

