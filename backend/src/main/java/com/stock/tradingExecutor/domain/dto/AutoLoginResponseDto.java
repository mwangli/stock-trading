package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自动登录响应 DTO
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AutoLoginResponseDto {

    private boolean success;

    private String message;

    private String token;
}
