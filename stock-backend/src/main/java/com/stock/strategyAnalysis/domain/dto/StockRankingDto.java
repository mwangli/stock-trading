package com.stock.strategyAnalysis.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票排名 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockRankingDto {

    private String stockCode;
    private String stockName;
    private double lstmScore;
    private double sentimentScore;
    private double totalScore;
    private int rank;
    private String reason;
}
