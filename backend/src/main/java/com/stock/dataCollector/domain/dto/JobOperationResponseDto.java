package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务操作通用响应 DTO
 *
 * 用于手动触发任务及其他 Job 操作接口返回。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobOperationResponseDto {

    private boolean success;

    private String message;
}

