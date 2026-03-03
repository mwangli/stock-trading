package com.stock.tradingExecutor.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 持仓信息
 */
@Data
public class Position {
    /**
     * 股票代码
     */
    private String stockCode;
    
    /**
     * 股票名称
     */
    private String stockName;
    
    /**
     * 持仓数量
     */
    private Integer quantity;
    
    /**
     * 平均成本
     */
    private BigDecimal avgCost;
    
    /**
     * 当前价格
     */
    private BigDecimal currentPrice;
    
    /**
     * 浮动盈亏
     */
    private BigDecimal profitLoss;
    
    /**
     * 盈亏比例
     */
    private BigDecimal profitLossPercent;
    
    /**
     * 买入日期 (T+1检查)
     */
    private LocalDate buyDate;
    
    /**
     * 市值
     */
    private BigDecimal marketValue;
    
    /**
     * 行业
     */
    private String industry;
    
    /**
     * 计算市值
     */
    public BigDecimal getMarketValue() {
        if (currentPrice != null && quantity != null) {
            return currentPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * 计算盈亏
     */
    public BigDecimal getProfitLoss() {
        if (avgCost != null && currentPrice != null && quantity != null) {
            return currentPrice.subtract(avgCost).multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * 计算盈亏比例
     */
    public BigDecimal getProfitLossPercent() {
        if (avgCost != null && currentPrice != null && avgCost.compareTo(BigDecimal.ZERO) > 0) {
            return currentPrice.subtract(avgCost).divide(avgCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }
}