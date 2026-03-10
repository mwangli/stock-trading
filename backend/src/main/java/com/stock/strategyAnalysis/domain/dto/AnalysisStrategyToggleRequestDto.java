package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分析页策略启用状态切换请求 DTO
 *
 * 对应 /api/strategy/analysis/active/{id} 接口的请求体。
 *
 * @author AI Assistant
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisStrategyToggleRequestDto {

    /**
     * 是否启用该策略
     */
    private boolean active;
}

