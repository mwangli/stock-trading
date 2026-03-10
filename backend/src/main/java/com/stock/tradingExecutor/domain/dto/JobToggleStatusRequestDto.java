package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台任务启停请求 DTO
 *
 * 对应 /api/jobs/status/{id} 接口的请求体。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobToggleStatusRequestDto {

    /**
     * 是否启用任务
     */
    private boolean active;
}

