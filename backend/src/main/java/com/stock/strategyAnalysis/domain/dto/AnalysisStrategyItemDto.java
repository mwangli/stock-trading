package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分析页策略项 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisStrategyItemDto {

    private String id;
    private String type;
    private String nameKey;
    private boolean active;
    private double winRate;
    private double pnl;
    private int totalTrades;
}
