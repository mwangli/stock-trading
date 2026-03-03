package com.stock.strategyAnalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 选股结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SelectionResult {
    
    /**
     * 执行时间
     */
    private LocalDateTime executeTime;
    
    /**
     * 参与选股总数
     */
    private int totalStocks;
    
    /**
     * Top N 结果
     */
    private List<StockRankingDto> topN;
    
    /**
     * 耗时（毫秒）
     */
    private long costTimeMs;
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String errorMessage;
}