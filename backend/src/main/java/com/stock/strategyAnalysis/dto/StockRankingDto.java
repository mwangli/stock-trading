package com.stock.strategyAnalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票排名DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockRankingDto {
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 股票名称
     */
    private String stockName;
    
    /**
     * LSTM因子得分
     */
    private double lstmScore;
    
    /**
     * 情感因子得分
     */
    private double sentimentScore;
    
    /**
     * 综合得分
     */
    private double totalScore;
    
    /**
     * 排名
     */
    private int rank;
    
    /**
     * 推荐理由
     */
    private String reason;
}