package com.stock.strategyAnalysis.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 股票排名实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockRanking {

    private String stockCode;
    private String stockName;
    private double lstmScore;
    private double sentimentScore;
    private double totalScore;
    private int rank;
    private LocalDateTime calculateTime;
    private String reason;
}
