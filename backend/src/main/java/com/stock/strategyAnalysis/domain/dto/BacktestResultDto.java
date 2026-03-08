package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 回测结果 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDto {

    private String resultId;
    private LocalDate startDate;
    private LocalDate endDate;
    private double totalReturn;
    private double annualizedReturn;
    private double maxDrawdown;
    private int totalTrades;
    private int winTrades;
    private double winRate;
    private double avgProfit;
    private double avgLoss;
    private double avgHighCaptureRate;
    private String calculateTime;
    private long costTimeMs;
}
