package com.stock.tradingExecutor.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后台任务 Cron 表达式更新请求 DTO
 *
 * 对应 /api/jobs/cron/{id} 接口的请求体。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobUpdateCronRequestDto {

    /**
     * Cron 表达式
     */
    private String cron;
}

