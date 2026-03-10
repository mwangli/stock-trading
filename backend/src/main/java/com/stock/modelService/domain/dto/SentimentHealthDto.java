package com.stock.modelService.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 情感分析服务健康状态 DTO
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentimentHealthDto {

    private String status;

    private String service;

    private boolean modelLoaded;

    private LocalDateTime lastLoadedTime;
}

