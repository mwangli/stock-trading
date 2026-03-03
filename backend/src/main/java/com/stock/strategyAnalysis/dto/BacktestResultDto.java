package com.stock.strategyAnalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 回测结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResultDto {
    
    /**
     * 结果ID
     */
    private String resultId;
    
    /**
     * 开始日期
     */
    private LocalDate startDate;
    
    /**
     * 结束日期
     */
    private LocalDate endDate;
    
    /**
     * 总收益率
     */
    private double totalReturn;
    
    /**
     * 年化收益率
     */
    private double annualizedReturn;
    
    /**
     * 最大回撤
     */
    private double maxDrawdown;
    
    /**
     * 总交易次数
     */
    private int totalTrades;
    
    /**
     * 盈利次数
     */
    private int winTrades;
    
    /**
     * 胜率
     */
    private double winRate;
    
    /**
     * 平均盈利
     */
    private double avgProfit;
    
    /**
     * 平均亏损
     */
    private double avgLoss;
    
    /**
     * 高点捕获率
     */
    private double avgHighCaptureRate;
    
    /**
     * 计算时间
     */
    private String calculateTime;
    
    /**
     * 耗时（毫秒）
     */
    private long costTimeMs;
}