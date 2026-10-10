package com.stock.tradingExecutor.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Data
public class AccountStatus {

    private BigDecimal totalAssets;
    private BigDecimal availableCash;
    private BigDecimal frozenAmount;
    private BigDecimal totalPosition;
    private Double dailyProfitLossPercent;
    private Double monthlyProfitLossPercent;
    private Double totalPositionPercent;

    public Double getDailyLossPercent() {
        return dailyProfitLossPercent != null ? Math.max(0, -dailyProfitLossPercent) : 0.0;
    }

    public Double getMonthlyLossPercent() {
        return monthlyProfitLossPercent != null ? Math.max(0, -monthlyProfitLossPercent) : 0.0;
    }

    public Double getTotalPositionPercent() {
        if (totalAssets != null && totalAssets.compareTo(BigDecimal.ZERO) > 0 && totalPosition != null) {
            return totalPosition.divide(totalAssets, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
        }
        return 0.0;
    }
}
