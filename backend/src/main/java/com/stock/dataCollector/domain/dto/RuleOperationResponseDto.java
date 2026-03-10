package com.stock.dataCollector.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则操作通用响应 DTO
 *
 * 用于新增、删除、更新规则等操作的统一返回结构。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleOperationResponseDto {

    private boolean success;

    private String message;
}

