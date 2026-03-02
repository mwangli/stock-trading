package com.stock.tradingExecutor.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 账户状态
 */
@Data
public class AccountStatus {
    /**
     * 总资产
     */
    private BigDecimal totalAssets;
    
    /**
     * 可用资金
     */
    private BigDecimal availableCash;
    
    /**
     * 冻结资金
     */
    private BigDecimal frozenAmount;
    
    /**
     * 总持仓市值
     */
    private BigDecimal totalPosition;
    
    /**
     * 当日盈亏比例
     */
    private Double dailyProfitLossPercent;
    
    /**
     * 当月盈亏比例
     */
    private Double monthlyProfitLossPercent;
    
    /**
     * 总仓位比例
     */
    private Double totalPositionPercent;
    
    /**
     * 获取当日亏损比例
     */
    public Double getDailyLossPercent() {
        return dailyProfitLossPercent != null ? Math.max(0, -dailyProfitLossPercent) : 0.0;
    }
    
    /**
     * 获取当月亏损比例
     */
    public Double getMonthlyLossPercent() {
        return monthlyProfitLossPercent != null ? Math.max(0, -monthlyProfitLossPercent) : 0.0;
    }
    
    /**
     * 计算总仓位比例
     */
    public Double getTotalPositionPercent() {
        if (totalAssets != null && totalAssets.compareTo(BigDecimal.ZERO) > 0 
                && totalPosition != null) {
            return totalPosition.divide(totalAssets, 4, BigDecimal.ROUND_HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        return 0.0;
    }
}