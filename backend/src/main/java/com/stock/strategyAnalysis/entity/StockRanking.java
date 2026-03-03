package com.stock.strategyAnalysis.entity;

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
    
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 股票名称
     */
    private String stockName;
    
    /**
     * LSTM因子得分 [0, 1]
     */
    private double lstmScore;
    
    /**
     * 情感因子得分 [0, 1]
     */
    private double sentimentScore;
    
    /**
     * 综合得分 [0, 1]
     */
    private double totalScore;
    
    /**
     * 排名
     */
    private int rank;
    
    /**
     * 计算时间
     */
    private LocalDateTime calculateTime;
    
    /**
     * 推荐理由
     */
    private String reason;
}