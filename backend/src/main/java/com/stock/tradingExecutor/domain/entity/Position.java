package com.stock.tradingExecutor.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 持仓信息
 */
@Data
public class Position {

    private String stockCode;
    private String stockName;
    private Integer quantity;
    private BigDecimal avgCost;
    private BigDecimal currentPrice;
    private BigDecimal profitLoss;
    private BigDecimal profitLossPercent;
    private LocalDate buyDate;
    private BigDecimal marketValue;
    private String industry;

    public BigDecimal getMarketValue() {
        if (currentPrice != null && quantity != null) {
            return currentPrice.multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getProfitLoss() {
        if (avgCost != null && currentPrice != null && quantity != null) {
            return currentPrice.subtract(avgCost).multiply(BigDecimal.valueOf(quantity));
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal getProfitLossPercent() {
        if (avgCost != null && currentPrice != null && avgCost.compareTo(BigDecimal.ZERO) > 0) {
            return currentPrice.subtract(avgCost).divide(avgCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        return BigDecimal.ZERO;
    }
}
